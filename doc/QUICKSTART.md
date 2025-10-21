# Быстрый старт - Корпоративный Мессенджер

## За 5 минут до запуска

### 1. Запуск сервера

```bash
cd server
chmod +x generate_certs.sh
./generate_certs.sh
python3 main.py
```

Сервер запустится на порту 12345. Вы увидите:
```
[+] Database initialized
[+] Server listening on 0.0.0.0:12345
```

### 2. Сборка Android приложения

1. Откройте Android Studio
2. Откройте проект (папка CorpMessenger)
3. Дождитесь синхронизации Gradle
4. Измените IP сервера в `LoginActivity.kt`:
   ```kotlin
   SocketManager.connect("YOUR_SERVER_IP", 12345)
   ```
   - Для эмулятора: `10.0.2.2`
   - Для реального устройства: IP вашего компьютера
5. Запустите приложение (Run > Run 'app')

### 3. Использование

#### Первый вход
- Логин: `user1`
- Пароль: `password1`

Другие тестовые пользователи: user2/password2, user3/password3, admin/admin

#### Отправка сообщения
1. После входа выберите контакт из списка
2. Введите сообщение
3. Нажмите "Send"

#### Отправка файла
1. В чате нажмите "Attach"
2. Выберите файл или фото
3. Файл будет отправлен

## Структура

```
CorpMessenger/
├── app/              # Android приложение
├── server/           # Python сервер
│   ├── main.py      # Запустите это!
│   ├── data/        # База данных
│   └── certs/       # SSL сертификаты
└── README.md        # Полная документация
```

## Устранение проблем

### Сервер не запускается
- Проверьте, что порт 12345 свободен: `netstat -an | grep 12345`
- Убедитесь, что сертификаты сгенерированы: `ls -la server/certs/`

### Приложение не подключается
- Проверьте, что сервер запущен
- Проверьте IP адрес в LoginActivity.kt
- Для эмулятора используйте `10.0.2.2`, а не `localhost`
- Отключите файрвол или разрешите порт 12345

### Ошибка сборки Android
- Убедитесь, что установлен Android SDK
- Проверьте подключение к интернету (для загрузки зависимостей)
- Попробуйте Sync Project with Gradle Files

## Дополнительно

### Создание своих пользователей

Подключитесь к базе данных:
```bash
cd server
sqlite3 data/messenger.db
```

Добавьте пользователя:
```sql
-- Сначала создайте хеш пароля в Python
-- python3 -c "import hashlib; print(hashlib.sha256(('PASSWORD' + 'SALT').encode()).hexdigest())"
INSERT INTO users (username, password_hash, salt) 
VALUES ('newuser', 'hash_here', 'salt_here');
```

### Перенос на продакшен сервер

1. Скопируйте папку `server/` на сервер
2. Настройте файрвол: `ufw allow 12345/tcp`
3. Используйте systemd для автозапуска:

```bash
sudo nano /etc/systemd/system/messenger.service
```

```ini
[Unit]
Description=Corporate Messenger Server
After=network.target

[Service]
Type=simple
User=messenger
WorkingDirectory=/opt/messenger
ExecStart=/usr/bin/python3 /opt/messenger/main.py
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable messenger
sudo systemctl start messenger
```

## Поддержка

См. полную документацию в [README.md](README.md)