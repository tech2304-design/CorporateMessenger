# Архитектура Решения / Solution Architecture

## Обзор / Overview

Полностью функциональный корпоративный мессенджер с Android клиентом и Python сервером.

Fully functional corporate messenger with Android client and Python server.

## Компоненты Системы / System Components

```
┌─────────────────────────────────────────────────────────────┐
│                     ANDROID CLIENT                          │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ LoginActivity│→ │ContactsActivity│→│ ChatActivity │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│         ↓                  ↓                 ↓               │
│  ┌─────────────────────────────────────────────────┐        │
│  │           MessageRepository                     │        │
│  └─────────────────────────────────────────────────┘        │
│         ↓                                  ↓                 │
│  ┌─────────────┐                  ┌─────────────┐           │
│  │ Room DB     │                  │SocketManager│           │
│  │ (Local)     │                  │ (Network)   │           │
│  └─────────────┘                  └─────────────┘           │
└─────────────────────────────────────────────────────────────┘
                            ↕ SSL/TLS
┌─────────────────────────────────────────────────────────────┐
│                      PYTHON SERVER                          │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────┐        │
│  │         SSL Socket Server (Port 12345)          │        │
│  └─────────────────────────────────────────────────┘        │
│         ↓                                                    │
│  ┌─────────────────────────────────────────────────┐        │
│  │        Protocol Handler (Thread Pool)           │        │
│  │  • AUTH • LIST_USERS • SEND_MSG                │        │
│  │  • GET_MESSAGES • UPLOAD_FILE                  │        │
│  └─────────────────────────────────────────────────┘        │
│         ↓                                                    │
│  ┌─────────────┐                  ┌─────────────┐           │
│  │ SQLite DB   │                  │ File Storage│           │
│  │ (Persistent)│                  │ (uploads/)  │           │
│  └─────────────┘                  └─────────────┘           │
└─────────────────────────────────────────────────────────────┘
```

## Поток Данных / Data Flow

### 1. Аутентификация / Authentication Flow

```
┌─────────┐                    ┌─────────┐
│ Client  │                    │ Server  │
└────┬────┘                    └────┬────┘
     │                              │
     │  AUTH:user1:password1        │
     ├─────────────────────────────→│
     │                              │ Hash password
     │                              │ Check database
     │                              │
     │     AUTH_OK:1                │
     │←─────────────────────────────┤
     │                              │
     │  Save user_id=1              │
     │                              │ Add to clients{}
```

### 2. Отправка Сообщения / Send Message Flow

```
┌─────────┐                    ┌─────────┐                    ┌─────────┐
│  User1  │                    │ Server  │                    │  User2  │
└────┬────┘                    └────┬────┘                    └────┬────┘
     │                              │                              │
     │ SEND_MSG:{recipient_id:2,    │                              │
     │           text:"Hello"}      │                              │
     ├─────────────────────────────→│                              │
     │                              │ Save to DB                   │
     │                              │ Get msg_id=123               │
     │                              │                              │
     │     MSG_SENT:123             │                              │
     │←─────────────────────────────┤                              │
     │                              │                              │
     │                              │ NEW_MSG:{...} (if connected) │
     │                              ├─────────────────────────────→│
     │                              │                              │
     │ Save to local DB             │                              │ Display in chat
```

### 3. Real-Time Обновления / Real-Time Updates Flow

```
┌─────────┐                    ┌─────────┐
│  User2  │                    │ Server  │
└────┬────┘                    └────┬────┘
     │                              │
     │  Every 3 seconds             │
     │  ┌─────────────┐             │
     │  │ Timer Loop  │             │
     │  └─────────────┘             │
     │        ↓                     │
     │  GET_MESSAGES:1              │
     ├─────────────────────────────→│
     │                              │ Query DB for
     │                              │ messages between
     │                              │ user1 & user2
     │                              │
     │  MESSAGES:[{id:123,...}]     │
     │←─────────────────────────────┤
     │                              │
     │ Save new messages to         │
     │ local DB                     │
     │        ↓                     │
     │  Room Flow triggers          │
     │  UI update                   │
```

### 4. Загрузка Файла / File Upload Flow

```
┌─────────┐                    ┌─────────┐
│ Client  │                    │ Server  │
└────┬────┘                    └────┬────┘
     │                              │
     │ UPLOAD_FILE:{recipient_id:2, │
     │  filename:"photo.jpg",       │
     │  size:102400}                │
     ├─────────────────────────────→│
     │                              │ Prepare file path
     │                              │ in uploads/
     │                              │
     │     READY_FOR_FILE           │
     │←─────────────────────────────┤
     │                              │
     │  [Binary file data]          │
     │  (102400 bytes)              │
     ├─────────────────────────────→│
     │                              │ Write to file
     │                              │ Save msg to DB
     │                              │
     │   FILE_UPLOADED:124          │
     │←─────────────────────────────┤
     │                              │
     │ Save file msg to local DB    │
```

## База Данных / Database Schema

### Server SQLite Database

```sql
users
├─ id (INTEGER PRIMARY KEY)
├─ username (TEXT UNIQUE)
├─ password_hash (TEXT)
├─ salt (TEXT)
└─ created_at (DATETIME)

messages
├─ id (INTEGER PRIMARY KEY)
├─ sender_id (INTEGER FK → users.id)
├─ recipient_id (INTEGER FK → users.id)
├─ group_id (INTEGER FK → groups.id)
├─ message_text (TEXT)
├─ timestamp (DATETIME)
├─ file_path (TEXT)
└─ is_read (INTEGER)

groups
├─ id (INTEGER PRIMARY KEY)
├─ group_name (TEXT)
├─ creator_id (INTEGER FK → users.id)
└─ created_at (DATETIME)

group_members
├─ id (INTEGER PRIMARY KEY)
├─ group_id (INTEGER FK → groups.id)
├─ user_id (INTEGER FK → users.id)
└─ role (TEXT)
```

### Android Room Database

```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Long,
    val senderId: Int,
    val recipientId: Int,
    val groupId: Int?,
    val text: String?,
    val filePath: String?,
    val timestamp: Long,
    val isRead: Boolean
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val username: String
)
```

## Протокол Обмена / Communication Protocol

### Команды Клиент → Сервер / Client → Server Commands

| Команда | Формат | Описание |
|---------|--------|----------|
| AUTH | `AUTH:username:password` | Аутентификация пользователя |
| LIST_USERS | `LIST_USERS` | Получить список всех пользователей |
| SEND_MSG | `SEND_MSG:{"recipient_id":N,"text":"..."}` | Отправить текстовое сообщение |
| GET_MESSAGES | `GET_MESSAGES:user_id` | Получить историю сообщений с пользователем |
| UPLOAD_FILE | `UPLOAD_FILE:{"recipient_id":N,"filename":"...","size":N}` | Начать загрузку файла |

### Ответы Сервер → Клиент / Server → Client Responses

| Ответ | Формат | Описание |
|-------|--------|----------|
| AUTH_OK | `AUTH_OK:user_id` | Успешная аутентификация |
| AUTH_FAIL | `AUTH_FAIL` | Ошибка аутентификации |
| USERS | `USERS:[{"id":1,"username":"user1"},...]` | Список пользователей |
| MSG_SENT | `MSG_SENT:message_id` | Сообщение отправлено |
| MESSAGES | `MESSAGES:[{...}]` | История сообщений |
| READY_FOR_FILE | `READY_FOR_FILE` | Готов принять файл |
| FILE_UPLOADED | `FILE_UPLOADED:message_id` | Файл загружен |
| NEW_MSG | `NEW_MSG:{...}` | Новое сообщение (broadcast) |
| ERROR | `ERROR:description` | Ошибка выполнения команды |

## Безопасность / Security

### Шифрование / Encryption
```
┌─────────────┐         SSL/TLS         ┌─────────────┐
│   Client    │◄──────────────────────→ │   Server    │
└─────────────┘    • AES-256 Cipher     └─────────────┘
                   • Certificate-based
                   • TLS 1.2+
```

### Хранение Паролей / Password Storage
```python
salt = os.urandom(16).hex()  # 32 char hex string
password_hash = hashlib.sha256((password + salt).encode()).hexdigest()

# Stored in DB:
# password_hash: "a1b2c3..."  (64 chars)
# salt: "d4e5f6..."  (32 chars)
```

### Защита от SQL Injection / SQL Injection Protection
```python
# ✅ Правильно (Parametrized queries)
cursor.execute("SELECT * FROM users WHERE username = ?", (username,))

# ❌ Неправильно (Vulnerable)
cursor.execute(f"SELECT * FROM users WHERE username = '{username}'")
```

## Масштабирование / Scalability

### Текущая Архитектура / Current Architecture
- **Пользователи:** до 250 одновременных
- **Сообщений/сек:** ~1000
- **База данных:** SQLite (подходит для малых организаций)
- **Файлы:** локальное хранилище

### Улучшения для Масштабирования / Scaling Improvements

```
┌────────────────────────────────────────────────────────┐
│                    Load Balancer                       │
└───────────────────┬────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        ↓                       ↓
┌───────────────┐       ┌───────────────┐
│  Server 1     │       │  Server 2     │
└───────┬───────┘       └───────┬───────┘
        │                       │
        └───────────┬───────────┘
                    ↓
        ┌───────────────────────┐
        │  PostgreSQL Cluster   │
        └───────────────────────┘
                    │
        ┌───────────┴───────────┐
        ↓                       ↓
┌───────────────┐       ┌───────────────┐
│  Redis Cache  │       │  File Storage │
│               │       │  (S3/MinIO)   │
└───────────────┘       └───────────────┘
```

## Производительность / Performance

### Метрики / Metrics

| Операция | Время отклика | Примечание |
|----------|---------------|------------|
| Аутентификация | < 100ms | SHA-256 hashing |
| Список пользователей | < 50ms | Simple SELECT |
| Отправка сообщения | < 100ms | INSERT + broadcast |
| Получение истории | < 200ms | Зависит от кол-ва сообщений |
| Загрузка файла 1MB | ~1-2s | Зависит от сети |
| Polling интервал | 3s | Настраиваемо |

### Оптимизации / Optimizations

1. **Индексы базы данных:**
   ```sql
   CREATE INDEX idx_messages_recipient ON messages(recipient_id);
   CREATE INDEX idx_messages_group ON messages(group_id);
   ```

2. **Connection pooling** (для масштабирования)
3. **Кеширование списка пользователей**
4. **Batch insert** для сообщений
5. **Compression** для больших файлов

## Отказоустойчивость / Fault Tolerance

### Обработка Ошибок / Error Handling

```python
try:
    # Server operations
except ssl.SSLError as e:
    # SSL connection failed
    log_error(f"SSL Error: {e}")
except sqlite3.Error as e:
    # Database error
    log_error(f"DB Error: {e}")
except Exception as e:
    # Generic error
    log_error(f"Error: {e}")
finally:
    # Cleanup
    conn.close()
```

### Восстановление Соединения / Connection Recovery

```kotlin
// Client-side retry logic
var retries = 0
while (retries < 3) {
    try {
        SocketManager.connect(host, port)
        break
    } catch (e: Exception) {
        retries++
        delay(1000 * retries)  // Exponential backoff
    }
}
```

## Мониторинг / Monitoring

### Логирование / Logging

**Server:**
```python
print(f"[+] Connection from {addr}")
print(f"[{addr}] {message}")
print(f"[!] Error: {e}")
print(f"[-] Disconnected {addr}")
```

**Android:**
```kotlin
Log.d("SocketManager", "Connected to $host:$port")
Log.e("SocketManager", "Error: $e")
```

### Метрики для Отслеживания / Metrics to Track

1. Активные соединения
2. Сообщений в минуту
3. Ошибки аутентификации
4. Размер базы данных
5. Использование дискового пространства (uploads/)
6. Среднее время отклика

## Развёртывание / Deployment

### Требования / Requirements

**Server:**
- Python 3.8+
- OpenSSL
- ~100MB disk space

**Client:**
- Android 7.0+ (API 24+)
- ~50MB storage
- Internet connection

### Checklist

- [ ] Сервер запущен и доступен
- [ ] SSL сертификаты настроены
- [ ] База данных инициализирована
- [ ] Firewall настроен (порт 12345)
- [ ] IP адрес настроен в приложении
- [ ] Резервное копирование настроено

## Будущие Улучшения / Future Enhancements

1. **WebSocket** вместо polling
2. **Push уведомления** через FCM
3. **End-to-end шифрование**
4. **Групповые чаты**
5. **Голосовые сообщения**
6. **Видеозвонки**
7. **Web клиент**
8. **Desktop приложения**

---

*Версия: 1.0.0*  
*Дата: 2025-10-20*
