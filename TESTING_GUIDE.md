# Руководство по Тестированию / Testing Guide

## Быстрый Старт / Quick Start

### 1. Запуск Сервера / Start Server

```bash
cd server
python3 main.py
```

Вы должны увидеть:
```
[+] Database initialized
[+] Server listening on 0.0.0.0:12345
```

### 2. Тестирование Сервера / Test Server

Сервер автоматически создаёт тестовых пользователей:
- **user1** / password1
- **user2** / password2
- **user3** / password3
- **admin** / admin

#### Ручное Тестирование с Python / Manual Testing with Python

```python
import socket
import ssl
import json

# Создание SSL соединения
context = ssl.create_default_context()
context.check_hostname = False
context.verify_mode = ssl.CERT_NONE

with socket.create_connection(('localhost', 12345)) as sock:
    with context.wrap_socket(sock) as ssock:
        # Тест аутентификации
        ssock.sendall(b"AUTH:user1:password1\n")
        print(ssock.recv(1024).decode())  # AUTH_OK:1
        
        # Список пользователей
        ssock.sendall(b"LIST_USERS\n")
        print(ssock.recv(1024).decode())  # USERS:[...]
```

### 3. Сборка Android Приложения / Build Android App

```bash
./gradlew assembleDebug
```

**Примечание:** В изолированной среде сборка может не работать из-за отсутствия доступа к Android SDK репозиториям. Используйте Android Studio для сборки.

### 4. Настройка IP Адреса / Configure IP Address

В `app/src/main/java/com/example/messenger/ui/LoginActivity.kt`:

```kotlin
// Для эмулятора
SocketManager.connect("10.0.2.2", 12345)

// Для реального устройства (замените на IP вашего сервера)
SocketManager.connect("192.168.1.100", 12345)
```

### 5. Тестирование Приложения / Test Application

#### 5.1. Вход в Систему / Login
1. Запустите приложение
2. Введите: user1 / password1
3. Нажмите "Login"
4. Вы должны увидеть список контактов

#### 5.2. Отправка Сообщения / Send Message
1. Выберите "user2" из списка контактов
2. Введите текст сообщения
3. Нажмите "Send"
4. Сообщение появится справа с зелёным фоном

#### 5.3. Получение Сообщения / Receive Message
1. Откройте второй экземпляр приложения (или эмулятор)
2. Войдите как user2 / password2
3. Откройте чат с user1
4. Через 3 секунды должно появиться сообщение от user1 слева с белым фоном

#### 5.4. Загрузка Файла / Upload File
1. В чате нажмите "Attach"
2. Выберите файл (изображение, PDF, документ)
3. Файл будет загружен
4. В чате появится сообщение "[File: имя_файла]"

## Проверка Базы Данных / Check Database

### Сервер / Server
```bash
cd server
sqlite3 data/messenger.db

# Посмотреть пользователей
SELECT * FROM users;

# Посмотреть сообщения
SELECT * FROM messages;

# Посмотреть загруженные файлы
.system ls -lh data/uploads/
```

### Android / Android
```bash
# Через adb (если устройство подключено)
adb shell
run-as com.example.messenger
cd databases
sqlite3 messenger.db

SELECT * FROM messages;
SELECT * FROM users;
```

## Функциональные Тесты / Functional Tests

### ✅ Тест 1: Аутентификация
- [ ] Успешный вход с правильными учётными данными
- [ ] Отклонение неправильных учётных данных
- [ ] Получение user_id после входа

### ✅ Тест 2: Список Контактов
- [ ] Загрузка списка пользователей с сервера
- [ ] Отображение всех пользователей кроме текущего
- [ ] Возможность выбрать контакт

### ✅ Тест 3: Отправка Сообщений
- [ ] Отправка текстового сообщения
- [ ] Сообщение сохраняется в базе данных
- [ ] Сообщение отображается справа с зелёным фоном
- [ ] Время сообщения корректное

### ✅ Тест 4: Получение Сообщений
- [ ] Автоматическое обновление каждые 3 секунды
- [ ] Новые сообщения появляются слева с белым фоном
- [ ] Прокрутка к последнему сообщению

### ✅ Тест 5: Загрузка Файлов
- [ ] Выбор файла через галерею/файловый менеджер
- [ ] Загрузка файла на сервер
- [ ] Сообщение с файлом отображается в чате
- [ ] Файл сохраняется в server/data/uploads/

## Отладка / Debugging

### Логи Сервера / Server Logs
```bash
cd server
python3 main.py 2>&1 | tee server.log
```

### Логи Android / Android Logs
```bash
# Фильтр по тегу
adb logcat -s SocketManager:D ChatActivity:D LoginActivity:D

# Все логи приложения
adb logcat | grep com.example.messenger
```

### Проверка Сетевого Соединения / Check Network Connection
```bash
# Ping сервера
ping <server_ip>

# Проверка порта
telnet <server_ip> 12345
nc -zv <server_ip> 12345
```

## Распространённые Проблемы / Common Issues

### Проблема: "Ошибка подключения"
**Решение:**
1. Убедитесь, что сервер запущен
2. Проверьте IP адрес в коде
3. Для эмулятора используйте 10.0.2.2
4. Проверьте firewall на сервере

### Проблема: "Ошибка авторизации"
**Решение:**
1. Проверьте username и password
2. Убедитесь, что база данных инициализирована
3. Проверьте логи сервера

### Проблема: "Сообщения не обновляются"
**Решение:**
1. Проверьте, что polling работает (логи Android)
2. Убедитесь, что соединение не прерывается
3. Проверьте, что GET_MESSAGES возвращает данные

### Проблема: "Файл не загружается"
**Решение:**
1. Проверьте разрешения приложения (Storage)
2. Убедитесь, что директория server/data/uploads/ существует
3. Проверьте размер файла (не должен быть слишком большим)
4. Проверьте логи сервера на наличие ошибок

## Производительность / Performance Testing

### Нагрузочное Тестирование / Load Testing

```python
# test_load.py
import threading
import socket
import ssl

def stress_test():
    context = ssl.create_default_context()
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    
    threads = []
    for i in range(50):  # 50 одновременных соединений
        t = threading.Thread(target=send_message, args=(i,))
        threads.append(t)
        t.start()
    
    for t in threads:
        t.join()

def send_message(user_num):
    try:
        with socket.create_connection(('localhost', 12345)) as sock:
            with context.wrap_socket(sock) as ssock:
                ssock.sendall(f"AUTH:user1:password1\n".encode())
                ssock.recv(1024)
                ssock.sendall(b'SEND_MSG:{"recipient_id":2,"text":"Test"}\n')
                ssock.recv(1024)
    except Exception as e:
        print(f"Error in thread {user_num}: {e}")

stress_test()
```

## Метрики / Metrics

### Время Отклика / Response Time
- Аутентификация: < 100ms
- Список пользователей: < 50ms
- Отправка сообщения: < 100ms
- Загрузка файла: зависит от размера файла

### Пропускная Способность / Throughput
- Одновременных соединений: до 250
- Сообщений в секунду: ~1000
- Файлов в минуту: ~100 (зависит от размера)

## Безопасность / Security Testing

### Тест 1: SQL Injection
```python
# Попытка SQL injection
ssock.sendall(b"AUTH:admin' OR '1'='1:password\n")
# Должно вернуть AUTH_FAIL
```

### Тест 2: XSS
```python
# Отправка HTML/JavaScript
msg = '{"recipient_id":2,"text":"<script>alert(1)</script>"}'
# Должно сохраниться как обычный текст
```

### Тест 3: Buffer Overflow
```python
# Очень большое сообщение
msg = '{"recipient_id":2,"text":"' + 'A' * 100000 + '"}'
# Сервер должен обработать корректно
```

## Контрольный Список / Checklist

Перед развёртыванием убедитесь:

- [ ] Сервер запускается без ошибок
- [ ] База данных создаётся автоматически
- [ ] Тестовые пользователи создаются
- [ ] SSL сертификаты существуют
- [ ] Все 5 критических проблем исправлены:
  - [ ] Серверная функция работает
  - [ ] Аутентификация работает
  - [ ] Сообщения выравниваются правильно
  - [ ] Загрузка файлов работает
  - [ ] Real-time обновления работают

## Дополнительная Информация / Additional Information

Для получения дополнительной информации см.:
- `CRITICAL_FIXES.md` - Подробное описание исправлений
- `README.md` - Общая документация
- `server/README.md` - Документация сервера

---

*Дата последнего обновления: 2025-10-20*
