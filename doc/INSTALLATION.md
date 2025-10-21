# Руководство по установке

## Требования

### Сервер
- Python 3.8 или выше
- OpenSSL
- Linux/macOS/Windows с поддержкой Python

### Android клиент
- Android Studio Arctic Fox (2020.3.1) или новее
- Android SDK 24+ (Android 7.0 Nougat)
- Устройство Android или эмулятор

## Пошаговая установка

### Часть 1: Установка сервера

#### 1.1. Клонирование репозитория
```bash
git clone https://github.com/tech2304-design/CorpMessenger.git
cd CorpMessenger/server
```

#### 1.2. Генерация SSL сертификатов
```bash
chmod +x generate_certs.sh
./generate_certs.sh
```

Или вручную:
```bash
mkdir -p certs
openssl req -x509 -newkey rsa:4096 -nodes \
  -keyout certs/key.pem \
  -out certs/cert.pem \
  -days 365 \
  -subj "/CN=messenger.local"
```

#### 1.3. Запуск сервера
```bash
python3 main.py
```

Вы должны увидеть:
```
[+] Database initialized
[+] Server listening on 0.0.0.0:12345
[+] Database: data/messenger.db
[+] Upload directory: data/uploads
```

### Часть 2: Сборка Android приложения

#### 2.1. Открыть проект
1. Запустите Android Studio
2. Выберите "Open an Existing Project"
3. Выберите папку `CorpMessenger`
4. Дождитесь завершения синхронизации Gradle (может занять несколько минут)

#### 2.2. Настроить IP сервера
Откройте файл:
```
app/src/main/java/com/example/messenger/ui/LoginActivity.kt
```

Найдите строку:
```kotlin
SocketManager.connect("10.0.2.2", 12345)
```

Замените IP адрес:
- **Для эмулятора Android**: оставьте `10.0.2.2` (это localhost хоста)
- **Для реального устройства**: используйте IP вашего компьютера в локальной сети
  - Windows: `ipconfig` → найдите IPv4 адрес
  - Linux/Mac: `ifconfig` или `ip addr` → найдите inet адрес

Пример для реального устройства:
```kotlin
SocketManager.connect("192.168.1.100", 12345)
```

#### 2.3. Подключить устройство

**Опция А: Эмулятор**
1. Tools → AVD Manager
2. Создайте виртуальное устройство (если нет)
3. Запустите эмулятор

**Опция Б: Реальное устройство**
1. Включите режим разработчика на устройстве:
   - Settings → About Phone → Нажмите на "Build Number" 7 раз
2. Включите USB отладку:
   - Settings → Developer Options → USB Debugging
3. Подключите устройство к компьютеру через USB
4. Разрешите отладку на устройстве

#### 2.4. Запустить приложение
1. Выберите устройство в верхней панели Android Studio
2. Нажмите зелёную кнопку "Run" (или Shift+F10)
3. Дождитесь сборки и установки приложения

### Часть 3: Первый запуск

#### 3.1. Проверка сервера
Убедитесь, что сервер запущен и слушает на порту 12345:
```bash
netstat -an | grep 12345
```

#### 3.2. Вход в приложение
1. Запустите приложение на устройстве
2. Введите учётные данные:
   - **Логин**: user1
   - **Пароль**: password1
3. Нажмите "Login"

Если всё настроено правильно, вы увидите список контактов.

#### 3.3. Тестовые пользователи
- user1 / password1
- user2 / password2
- user3 / password3
- admin / admin

## Устранение проблем

### Сервер не запускается

**Проблема**: `Address already in use`
```bash
# Найдите процесс, использующий порт 12345
lsof -i :12345
# Или
netstat -tupln | grep 12345

# Остановите процесс
kill <PID>
```

**Проблема**: `Permission denied` на порту
```bash
# Используйте порт выше 1024 или запустите с sudo (не рекомендуется)
# Измените PORT в main.py на 12345 (уже выше 1024)
```

**Проблема**: Сертификаты не найдены
```bash
# Убедитесь, что сертификаты созданы
ls -la certs/
# Должны быть: cert.pem и key.pem
```

### Приложение не подключается

**Проблема**: "Connection refused"
- Убедитесь, что сервер запущен
- Проверьте IP адрес в LoginActivity.kt
- Для эмулятора используйте `10.0.2.2`
- Для устройства - убедитесь, что оно в той же сети

**Проблема**: "Connection timeout"
- Проверьте файрвол на сервере
- Для Linux: `sudo ufw allow 12345/tcp`
- Для Windows: добавьте правило в Windows Firewall

**Проблема**: SSL/TLS ошибка
- Это нормально для самоподписанных сертификатов
- В продакшене используйте сертификаты от Let's Encrypt

### Ошибки сборки Android

**Проблема**: Gradle sync failed
```bash
# В Android Studio:
File → Invalidate Caches / Restart
```

**Проблема**: SDK not found
```bash
# В Android Studio:
Tools → SDK Manager
# Установите Android SDK Platform 33
```

**Проблема**: Kotlin compiler error
```bash
# Убедитесь, что версия Kotlin совместима
# В build.gradle проверьте версию: 1.8.22
```

## Дополнительная настройка

### Настройка продакшен сервера

#### Linux (Ubuntu/Debian)

1. **Создайте пользователя**:
```bash
sudo useradd -m -s /bin/bash messenger
sudo su - messenger
```

2. **Установите проект**:
```bash
cd /opt
sudo git clone https://github.com/tech2304-design/CorpMessenger.git
sudo chown -R messenger:messenger CorpMessenger
```

3. **Создайте systemd сервис**:
```bash
sudo nano /etc/systemd/system/messenger.service
```

Содержимое:
```ini
[Unit]
Description=Corporate Messenger Server
After=network.target

[Service]
Type=simple
User=messenger
WorkingDirectory=/opt/CorpMessenger/server
ExecStart=/usr/bin/python3 /opt/CorpMessenger/server/main.py
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

4. **Запустите сервис**:
```bash
sudo systemctl daemon-reload
sudo systemctl enable messenger
sudo systemctl start messenger
sudo systemctl status messenger
```

#### Настройка файрвола

**Ubuntu/Debian**:
```bash
sudo ufw allow 12345/tcp
sudo ufw reload
```

**CentOS/RHEL**:
```bash
sudo firewall-cmd --permanent --add-port=12345/tcp
sudo firewall-cmd --reload
```

### Резервное копирование

Создайте cron задачу для автоматического резервного копирования:
```bash
crontab -e
```

Добавьте:
```
0 2 * * * /usr/bin/python3 /opt/CorpMessenger/server/backup_db.py
```

## Следующие шаги

1. Измените пароли по умолчанию
2. Настройте SSL сертификаты от Let's Encrypt
3. Настройте мониторинг сервера
4. Создайте резервные копии базы данных
5. Добавьте своих пользователей

## Поддержка

Для получения помощи:
- Проверьте [README.md](README.md) для общей информации
- Проверьте [QUICKSTART.md](QUICKSTART.md) для быстрого старта
- Откройте issue на GitHub
