# Руководство по быстрому исправлению критических проблем

Это пошаговое руководство поможет быстро исправить критические проблемы и запустить рабочий мессенджер.

**Время на исправление:** 2-3 дня работы

---

## Шаг 1: Исправление серверной логики (4-6 часов)

### 1.1 Замените содержимое `server/main.py`

Текущий сервер только отвечает "ACK" и ничего не делает. Замените его на рабочую версию:

```python
import socket
import ssl
import threading
import sqlite3
import json
import hashlib
import os
from datetime import datetime

HOST = '0.0.0.0'
PORT = 12345
CERTFILE = 'certs/cert.pem'
KEYFILE = 'certs/key.pem'
DB_PATH = 'data/messenger.db'

# Словарь активных соединений: {user_id: conn}
active_connections = {}
connections_lock = threading.Lock()

def init_database():
    """Инициализация базы данных и тестовых пользователей"""
    os.makedirs('data', exist_ok=True)
    os.makedirs('data/uploads', exist_ok=True)
    
    conn = sqlite3.connect(DB_PATH)
    
    # Создать схему если БД новая
    with open('schema.sql', 'r') as f:
        conn.executescript(f.read())
    
    # Добавить тестовых пользователей
    cursor = conn.cursor()
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
            pass  # Пользователь уже существует
    
    conn.commit()
    conn.close()
    print("[+] Database initialized with test users")

def authenticate(username, password):
    """Проверка учетных данных"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT id, password_hash, salt FROM users WHERE username = ?", (username,))
    result = cursor.fetchone()
    conn.close()
    
    if result:
        user_id, stored_hash, salt = result
        password_hash = hashlib.sha256((password + salt).encode()).hexdigest()
        if password_hash == stored_hash:
            return user_id
    return None

def get_users_list():
    """Получить список всех пользователей"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT id, username FROM users")
    users = [{"id": row[0], "username": row[1]} for row in cursor.fetchall()]
    conn.close()
    return users

def save_message(sender_id, recipient_id, text, file_path=None):
    """Сохранить сообщение в БД"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO messages (sender_id, recipient_id, message_text, file_path) VALUES (?, ?, ?, ?)",
        (sender_id, recipient_id, text, file_path)
    )
    message_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return message_id

def broadcast_message(sender_id, recipient_id, message_id, text):
    """Отправить уведомление получателю, если он онлайн"""
    with connections_lock:
        if recipient_id in active_connections:
            try:
                conn = active_connections[recipient_id]
                notification = {
                    "message_id": message_id,
                    "sender_id": sender_id,
                    "text": text,
                    "timestamp": datetime.now().isoformat()
                }
                conn.sendall(f"NEW_MSG:{json.dumps(notification)}\n".encode())
            except:
                pass  # Соединение разорвано

def handle_client(conn, addr):
    """Обработка клиентского соединения"""
    print(f"[+] Connection from {addr}")
    current_user_id = None
    
    try:
        while True:
            data = conn.recv(4096)
            if not data:
                break
            
            command = data.decode().strip()
            print(f"[{addr}] {command[:100]}...")  # Лог (ограничено 100 символов)
            
            # Обработка команд
            if command.startswith("AUTH:"):
                parts = command.split(":", 2)
                if len(parts) == 3:
                    username, password = parts[1], parts[2]
                    user_id = authenticate(username, password)
                    if user_id:
                        current_user_id = user_id
                        with connections_lock:
                            active_connections[user_id] = conn
                        conn.sendall(f"ACK:{user_id}\n".encode())
                        print(f"[+] User {username} (ID={user_id}) authenticated")
                    else:
                        conn.sendall(b"FAIL:Invalid credentials\n")
                else:
                    conn.sendall(b"FAIL:Invalid AUTH format\n")
            
            elif command == "LIST_USERS":
                users = get_users_list()
                response = f"USERS:{json.dumps(users)}\n"
                conn.sendall(response.encode())
            
            elif command.startswith("SEND_MSG:"):
                if not current_user_id:
                    conn.sendall(b"FAIL:Not authenticated\n")
                    continue
                
                try:
                    json_str = command[9:]
                    msg_data = json.loads(json_str)
                    recipient_id = msg_data.get("recipient_id")
                    text = msg_data.get("text", "")
                    
                    # Сохранить в БД
                    message_id = save_message(current_user_id, recipient_id, text)
                    conn.sendall(f"MSG_SENT:{message_id}\n".encode())
                    
                    # Отправить получателю, если он онлайн
                    broadcast_message(current_user_id, recipient_id, message_id, text)
                    
                except Exception as e:
                    conn.sendall(f"FAIL:{str(e)}\n".encode())
            
            elif command.startswith("UPLOAD_FILE:"):
                if not current_user_id:
                    conn.sendall(b"FAIL:Not authenticated\n")
                    continue
                
                try:
                    # Формат: UPLOAD_FILE:{"recipient_id":N,"filename":"name.ext","size":1234}
                    json_str = command[12:]
                    file_data = json.loads(json_str)
                    recipient_id = file_data.get("recipient_id")
                    filename = file_data.get("filename")
                    size = file_data.get("size", 0)
                    
                    # Принять файл
                    conn.sendall(b"READY\n")
                    file_bytes = b""
                    while len(file_bytes) < size:
                        chunk = conn.recv(min(8192, size - len(file_bytes)))
                        if not chunk:
                            break
                        file_bytes += chunk
                    
                    # Сохранить файл
                    safe_filename = f"{datetime.now().strftime('%Y%m%d_%H%M%S')}_{filename}"
                    file_path = os.path.join('data/uploads', safe_filename)
                    with open(file_path, 'wb') as f:
                        f.write(file_bytes)
                    
                    # Сохранить в БД
                    message_id = save_message(current_user_id, recipient_id, None, file_path)
                    conn.sendall(f"FILE_SENT:{message_id}\n".encode())
                    
                    print(f"[+] File uploaded: {file_path}")
                    
                except Exception as e:
                    conn.sendall(f"FAIL:{str(e)}\n".encode())
            
            elif command == "PING":
                conn.sendall(b"PONG\n")
            
            else:
                conn.sendall(b"FAIL:Unknown command\n")
    
    except Exception as e:
        print(f"[!] Error with {addr}: {e}")
    finally:
        if current_user_id:
            with connections_lock:
                active_connections.pop(current_user_id, None)
        conn.close()
        print(f"[-] Disconnected {addr}")

def start_server():
    """Запуск сервера"""
    print("[*] Initializing server...")
    init_database()
    
    context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
    context.load_cert_chain(certfile=CERTFILE, keyfile=KEYFILE)
    context.verify_mode = ssl.CERT_NONE
    
    bindsocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    bindsocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    bindsocket.bind((HOST, PORT))
    bindsocket.listen(50)
    print(f"[+] Server listening on {HOST}:{PORT}")
    print("[+] Test users: user1/password1, user2/password2, user3/password3, admin/admin")
    
    while True:
        try:
            newsocket, fromaddr = bindsocket.accept()
            try:
                sslconn = context.wrap_socket(newsocket, server_side=True)
                threading.Thread(target=handle_client, args=(sslconn, fromaddr), daemon=True).start()
            except ssl.SSLError as e:
                print(f"[!] SSL error from {fromaddr}: {e}")
                newsocket.close()
        except KeyboardInterrupt:
            print("\n[*] Shutting down...")
            break
        except Exception as e:
            print(f"[!] Error accepting connection: {e}")

if __name__ == "__main__":
    start_server()
```

### 1.2 Запустите сервер

```bash
cd server
python3 main.py
```

Вы должны увидеть:
```
[*] Initializing server...
[+] Database initialized with test users
[+] Server listening on 0.0.0.0:12345
[+] Test users: user1/password1, user2/password2, user3/password3, admin/admin
```

---

## Шаг 2: Исправление Android-клиента (3-4 часа)

### 2.1 Создайте глобальное хранилище для user_id

Создайте файл `app/src/main/java/com/example/messenger/data/UserSession.kt`:

```kotlin
package com.example.messenger.data

import android.content.Context
import android.content.SharedPreferences

object UserSession {
    private const val PREFS_NAME = "MessengerPrefs"
    private const val KEY_USER_ID = "userId"
    private const val KEY_USERNAME = "username"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveUser(userId: Int, username: String) {
        prefs.edit()
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
    }
    
    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)
    
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    
    fun isLoggedIn(): Boolean = getUserId() > 0
    
    fun logout() {
        prefs.edit().clear().apply()
    }
}
```

### 2.2 Обновите LoginActivity

Измените `LoginActivity.kt`:

```kotlin
// После успешной авторизации:
val response = SocketManager.readLine()
if (response?.startsWith("ACK:") == true) {
    val userId = response.substringAfter("ACK:").toIntOrNull() ?: 0
    
    // Сохранить user_id
    UserSession.saveUser(userId, username)
    
    Toast.makeText(this@LoginActivity, "Авторизация успешна", Toast.LENGTH_SHORT).show()
    
    // НЕ ЗАКРЫВАТЬ соединение!
    // SocketManager.disconnect() ← УДАЛИТЬ ЭТУ СТРОКУ
    
    // Navigate to contacts
    val intent = Intent(this@LoginActivity, ContactsActivity::class.java)
    startActivity(intent)
    finish()
} else {
    Toast.makeText(this@LoginActivity, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
    SocketManager.disconnect()
}
```

### 2.3 Обновите MessageRepository

Измените `sendText()` в `MessageRepository.kt`:

```kotlin
fun sendText(recipientId: Int, text: String) {
    CoroutineScope(Dispatchers.IO).launch {
        val currentUserId = UserSession.getUserId()  // ← Получаем реальный ID
        
        val msg = MessageEntity(
            senderId = currentUserId,  // ← Используем реальный ID
            recipientId = recipientId,
            groupId = null,
            text = text,
            filePath = null,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        insertLocal(msg)
        
        // Отправить на сервер
        try {
            val json = """{"recipient_id":$recipientId,"text":"$text"}"""
            SocketManager.sendLine("SEND_MSG:$json")
            val response = SocketManager.readLine()
            if (response?.startsWith("MSG_SENT:") == true) {
                val msgId = response.substringAfter("MSG_SENT:").toIntOrNull()
                // Успешно отправлено
            }
        } catch (e: Exception) {
            // Обработать ошибку
        }
    }
}
```

### 2.4 Инициализируйте UserSession

В `ContactsActivity.onCreate()` добавьте в начало:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_contacts)
    
    // Инициализировать UserSession
    UserSession.init(applicationContext)  // ← ДОБАВИТЬ
    MessageRepository.init(applicationContext)
    
    // ... остальной код
}
```

### 2.5 Добавьте SSL Trust Manager

Создайте файл `app/src/main/java/com/example/messenger/network/TrustAllCerts.kt`:

```kotlin
package com.example.messenger.network

import java.security.cert.X509Certificate
import javax.net.ssl.*

object TrustAllCerts {
    fun getInsecureSSLContext(): SSLContext {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return sslContext
    }
}
```

Обновите `SocketManager.connect()`:

```kotlin
fun connect(host: String, port: Int) {
    try {
        val sslContext = TrustAllCerts.getInsecureSSLContext()  // ← Используем кастомный context
        val factory = sslContext.socketFactory
        socket = factory.createSocket(host, port) as Socket
        writer = PrintWriter(socket!!.getOutputStream(), true)
        reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
        Log.d("SocketManager", "Connected to $host:$port")
    } catch (e: Exception) {
        Log.e("SocketManager", "Error connecting: $e")
        throw e
    }
}
```

### 2.6 Реализуйте синхронизацию контактов

Обновите `ContactsActivity.kt`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_contacts)
    
    UserSession.init(applicationContext)
    MessageRepository.init(applicationContext)
    
    // ... adapter setup ...
    
    // Загрузить контакты с сервера
    scope.launch(Dispatchers.IO) {
        try {
            SocketManager.sendLine("LIST_USERS")
            val response = SocketManager.readLine()
            
            if (response?.startsWith("USERS:") == true) {
                val jsonStr = response.substringAfter("USERS:")
                val usersJson = JSONArray(jsonStr)
                
                val db = AppDatabase.getInstance(this@ContactsActivity)
                for (i in 0 until usersJson.length()) {
                    val userObj = usersJson.getJSONObject(i)
                    val user = UserEntity(
                        id = userObj.getInt("id"),
                        username = userObj.getString("username"),
                        passwordHash = "",
                        salt = ""
                    )
                    db.userDao().insert(user)
                }
            }
        } catch (e: Exception) {
            Log.e("ContactsActivity", "Error loading users: $e")
        }
    }
    
    // Загрузка из локальной БД (как раньше)
    val db = AppDatabase.getInstance(this)
    scope.launch {
        db.userDao().getAllFlow().collect { users ->
            runOnUiThread {
                adapter.submitList(users)
            }
        }
    }
}
```

Добавьте импорт:
```kotlin
import org.json.JSONArray
```

---

## Шаг 3: Тестирование (1 час)

### 3.1 Запустите сервер

```bash
cd server
python3 main.py
```

### 3.2 Соберите и запустите Android приложение

1. Откройте проект в Android Studio
2. Запустите эмулятор или подключите устройство
3. Run → Run 'app'

### 3.3 Проверьте базовый сценарий

1. **Авторизация:**
   - Введите: user1 / password1
   - Нажмите Login
   - Должен открыться экран контактов

2. **Список контактов:**
   - Должны появиться: user1, user2, user3, admin

3. **Отправка сообщения:**
   - Откройте чат с user2
   - Введите текст и отправьте
   - Проверьте логи сервера - должно появиться: `SEND_MSG:...`

4. **Второе устройство:**
   - Откройте второй эмулятор
   - Войдите как user2
   - Откройте чат с user1
   - Отправьте сообщение
   - На первом устройстве должно появиться уведомление (если реализован слушатель)

---

## Шаг 4: Известные ограничения после исправлений

После выполнения этих шагов мессенджер будет работать, но с ограничениями:

### Что работает ✅
- Авторизация
- Список контактов
- Отправка сообщений (сохраняются на сервере)
- Отправка файлов (базовая реализация)

### Что НЕ работает ❌
- Real-time получение сообщений (нужен фоновый слушатель)
- Push-уведомления
- Синхронизация между устройствами
- История сообщений с сервера

### Следующие шаги
1. Реализовать фоновый Service для прослушивания NEW_MSG
2. Добавить получение истории сообщений при открытии чата
3. Реализовать полноценную загрузку файлов
4. Добавить обработку ошибок в UI

---

## Дополнительные файлы для справки

- **Полный отчет:** [AUDIT_REPORT.md](AUDIT_REPORT.md)
- **Краткое резюме:** [AUDIT_SUMMARY.md](AUDIT_SUMMARY.md)
- **Чеклист исправлений:** [FIXES_CHECKLIST.md](FIXES_CHECKLIST.md)

---

## Помощь и поддержка

При возникновении проблем:
1. Проверьте логи сервера: `server/logs/`
2. Проверьте Logcat в Android Studio
3. Убедитесь, что порт 12345 открыт
4. Проверьте IP адрес (для реального устройства не 10.0.2.2)

**Удачи в разработке!** 🚀
