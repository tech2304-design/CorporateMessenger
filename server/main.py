import socket
import ssl
import threading

HOST = '0.0.0.0'
PORT = 12345

CERTFILE = 'certs/cert.pem'
KEYFILE = 'certs/key.pem'

def handle_client(conn, addr):
    print(f"[+] Connection from {addr}")
    try:
        while True:
            data = conn.recv(1024)
            if not data:
                break
            print(f"[{addr}] {data.decode().strip()}")
            conn.sendall(b"ACK\n")
    except Exception as e:
        print(f"[!] Error with {addr}: {e}")
    finally:
        conn.close()
        print(f"[-] Disconnected {addr}")

def start_server():
    context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
    context.load_cert_chain(certfile=CERTFILE, keyfile=KEYFILE)
    context.verify_mode = ssl.CERT_NONE  # для теста, не требует клиентский сертификат

    bindsocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
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
