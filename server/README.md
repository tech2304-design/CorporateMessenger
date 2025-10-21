Корпоративный мессенджер — набор файлов
Содержимое архива:
- server/main.py — TLS многопоточный сервер (пример)
- schema.sql — схема БД SQLite
- backup_db.py — скрипт резервного копирования
- android-messenger/ — skeleton Android project (Kotlin) with core files

Инструкции быстрого старта (сервер):
1. Сгенерируйте сертификат:
   mkdir -p certs
   openssl req -x509 -newkey rsa:4096 -nodes -keyout certs/server.key -out certs/server.crt -days 365 -subj "/CN=messenger.local"
2. Убедитесь, что Python 3.8+ установлен.
3. Запустите: python server/main.py
4. Используйте openssl s_client или клиент из android проекта.

Android project: skeleton includes SocketManager, Room entities/DAO, basic Activities and ViewModel. Дополните Firebase configuration (google-services.json) для FCM.
