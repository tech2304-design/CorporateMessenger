import socket
import ssl
import threading
import sqlite3
import hashlib
import os
import json
import time

HOST = '0.0.0.0'
PORT = 12345

CERTFILE = 'certs/cert.pem'
KEYFILE = 'certs/key.pem'
DB_PATH = 'data/messenger.db'
UPLOAD_DIR = 'data/uploads'

# Global client connections dictionary
clients = {}  # {user_id: conn}
clients_lock = threading.Lock()

def init_database():
    """Initialize database with schema and test users"""
    os.makedirs('data', exist_ok=True)
    os.makedirs(UPLOAD_DIR, exist_ok=True)
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Execute schema
    with open('schema.sql', 'r') as f:
        cursor.executescript(f.read())
    
    # Create test users if not exist
    test_users = [
        ('user1', 'password1'),
        ('user2', 'password2'),
        ('user3', 'password3'),
        ('admin', 'admin')
    ]
    
    for username, password in test_users:
        salt = os.urandom(16).hex()
        password_hash = hashlib.sha256((password + salt).encode()).hexdigest()
        try:
            cursor.execute(
                "INSERT INTO users (username, password_hash, salt) VALUES (?, ?, ?)",
                (username, password_hash, salt)
            )
        except sqlite3.IntegrityError:
            pass  # User already exists
    
    conn.commit()
    conn.close()
    print("[+] Database initialized")

def authenticate(username, password):
    """Authenticate user and return user_id or None"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute(
        "SELECT id, password_hash, salt FROM users WHERE username = ?",
        (username,)
    )
    row = cursor.fetchone()
    conn.close()
    
    if not row:
        return None
    
    user_id, stored_hash, salt = row
    password_hash = hashlib.sha256((password + salt).encode()).hexdigest()
    
    if password_hash == stored_hash:
        return user_id
    return None

def get_users_list():
    """Get list of all users"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT id, username FROM users")
    users = [{"id": row[0], "username": row[1]} for row in cursor.fetchall()]
    conn.close()
    return users

def save_message(sender_id, recipient_id, text, file_path=None):
    """Save message to database and return message_id"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO messages (sender_id, recipient_id, message_text, file_path, timestamp) VALUES (?, ?, ?, ?, ?)",
        (sender_id, recipient_id, text, file_path, time.time())
    )
    message_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return message_id

def get_messages(user_id, other_user_id):
    """Get messages between two users"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute(
        """SELECT id, sender_id, recipient_id, message_text, file_path, timestamp 
           FROM messages 
           WHERE (sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?)
           ORDER BY timestamp ASC""",
        (user_id, other_user_id, other_user_id, user_id)
    )
    messages = []
    for row in cursor.fetchall():
        messages.append({
            "id": row[0],
            "sender_id": row[1],
            "recipient_id": row[2],
            "text": row[3],
            "file_path": row[4],
            "timestamp": row[5]
        })
    conn.close()
    return messages

def broadcast_message(sender_id, recipient_id, message_data):
    """Send message to recipient if they're connected"""
    with clients_lock:
        if recipient_id in clients:
            try:
                clients[recipient_id].sendall(f"NEW_MSG:{json.dumps(message_data)}\n".encode())
            except:
                pass  # Connection might be dead

def handle_client(conn, addr):
    print(f"[+] Connection from {addr}")
    user_id = None
    
    try:
        while True:
            data = conn.recv(4096)
            if not data:
                break
            
            message = data.decode().strip()
            print(f"[{addr}] {message}")
            
            # AUTH:username:password
            if message.startswith("AUTH:"):
                parts = message.split(":", 2)
                if len(parts) == 3:
                    username = parts[1]
                    password = parts[2]
                    user_id = authenticate(username, password)
                    if user_id:
                        with clients_lock:
                            clients[user_id] = conn
                        conn.sendall(f"AUTH_OK:{user_id}\n".encode())
                        print(f"[+] User {username} (ID: {user_id}) authenticated")
                    else:
                        conn.sendall(b"AUTH_FAIL\n")
                else:
                    conn.sendall(b"AUTH_FAIL\n")
            
            # LIST_USERS
            elif message == "LIST_USERS":
                users = get_users_list()
                conn.sendall(f"USERS:{json.dumps(users)}\n".encode())
            
            # SEND_MSG:{"recipient_id":N,"text":"..."}
            elif message.startswith("SEND_MSG:"):
                if not user_id:
                    conn.sendall(b"ERROR:Not authenticated\n")
                    continue
                
                try:
                    json_data = message[9:]  # Remove "SEND_MSG:"
                    data = json.loads(json_data)
                    recipient_id = data.get("recipient_id")
                    text = data.get("text")
                    
                    if recipient_id and text:
                        msg_id = save_message(user_id, recipient_id, text)
                        conn.sendall(f"MSG_SENT:{msg_id}\n".encode())
                        
                        # Broadcast to recipient
                        message_data = {
                            "id": msg_id,
                            "sender_id": user_id,
                            "recipient_id": recipient_id,
                            "text": text,
                            "timestamp": time.time()
                        }
                        broadcast_message(user_id, recipient_id, message_data)
                    else:
                        conn.sendall(b"ERROR:Invalid message format\n")
                except json.JSONDecodeError:
                    conn.sendall(b"ERROR:Invalid JSON\n")
            
            # GET_MESSAGES:other_user_id
            elif message.startswith("GET_MESSAGES:"):
                if not user_id:
                    conn.sendall(b"ERROR:Not authenticated\n")
                    continue
                
                try:
                    other_user_id = int(message.split(":", 1)[1])
                    messages = get_messages(user_id, other_user_id)
                    conn.sendall(f"MESSAGES:{json.dumps(messages)}\n".encode())
                except:
                    conn.sendall(b"ERROR:Invalid request\n")
            
            # UPLOAD_FILE:{"recipient_id":N,"filename":"...","size":N}
            elif message.startswith("UPLOAD_FILE:"):
                if not user_id:
                    conn.sendall(b"ERROR:Not authenticated\n")
                    continue
                
                try:
                    json_data = message[12:]  # Remove "UPLOAD_FILE:"
                    data = json.loads(json_data)
                    recipient_id = data.get("recipient_id")
                    filename = data.get("filename")
                    file_size = data.get("size", 0)
                    
                    if recipient_id and filename:
                        # Generate unique filename
                        unique_filename = f"{int(time.time())}_{filename}"
                        file_path = os.path.join(UPLOAD_DIR, unique_filename)
                        
                        conn.sendall(b"READY_FOR_FILE\n")
                        
                        # Receive file data
                        received = 0
                        with open(file_path, 'wb') as f:
                            while received < file_size:
                                chunk = conn.recv(min(8192, file_size - received))
                                if not chunk:
                                    break
                                f.write(chunk)
                                received += len(chunk)
                        
                        # Save message with file reference
                        msg_id = save_message(user_id, recipient_id, f"[File: {filename}]", file_path)
                        conn.sendall(f"FILE_UPLOADED:{msg_id}\n".encode())
                        
                        # Broadcast to recipient
                        message_data = {
                            "id": msg_id,
                            "sender_id": user_id,
                            "recipient_id": recipient_id,
                            "text": f"[File: {filename}]",
                            "file_path": file_path,
                            "timestamp": time.time()
                        }
                        broadcast_message(user_id, recipient_id, message_data)
                    else:
                        conn.sendall(b"ERROR:Invalid upload request\n")
                except Exception as e:
                    conn.sendall(f"ERROR:{str(e)}\n".encode())
            
            else:
                conn.sendall(b"ERROR:Unknown command\n")
                
    except Exception as e:
        print(f"[!] Error with {addr}: {e}")
    finally:
        if user_id:
            with clients_lock:
                clients.pop(user_id, None)
        conn.close()
        print(f"[-] Disconnected {addr}")

def start_server():
    init_database()
    
    context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
    context.load_cert_chain(certfile=CERTFILE, keyfile=KEYFILE)
    context.verify_mode = ssl.CERT_NONE  # для теста, не требует клиентский сертификат

    bindsocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    bindsocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    bindsocket.bind((HOST, PORT))
    bindsocket.listen(5)
    print(f"[+] Server listening on {HOST}:{PORT}")

    while True:
        try:
            newsocket, fromaddr = bindsocket.accept()
            try:
                conn = context.wrap_socket(newsocket, server_side=True)
                threading.Thread(target=handle_client, args=(conn, fromaddr), daemon=True).start()
            except ssl.SSLError as e:
                print(f"[!] SSL error from {fromaddr}: {e}")
                newsocket.close()
        except Exception as e:
            print(f"[!] Error accepting connection: {e}")

if __name__ == "__main__":
    start_server()
