# Quick Fix Guide - App Crashes on Real Device

## Problem
Приложение падает после авторизации на реальном устройстве (работает только на эмуляторе).

## Root Cause / Причина
IP адрес `10.0.2.2` работает только в эмуляторе Android и не работает на реальных устройствах.

## Solution / Решение

### 1. Найдите IP адрес вашего сервера

**На компьютере с сервером:**

Linux/Mac:
```bash
ifconfig | grep "inet "
```

Windows:
```cmd
ipconfig
```

Ищите адрес вида `192.168.x.x` или `10.0.x.x`

**Важно:** Устройство и сервер должны быть в одной Wi-Fi сети!

### 2. Откройте файл конфигурации

Файл: `app/src/main/java/com/example/messenger/network/ServerConfig.kt`

### 3. Измените IP адрес

**Было:**
```kotlin
const val SERVER_HOST = "10.0.2.2"
```

**Стало (пример):**
```kotlin
const val SERVER_HOST = "192.168.1.100"  // Ваш IP
```

### 4. Пересоберите приложение

В Android Studio:
1. **Build** → **Clean Project**
2. **Build** → **Rebuild Project**
3. **Run** → **Run 'app'**

## Примеры конфигурации

| Scenario | SERVER_HOST |
|----------|-------------|
| Эмулятор | `"10.0.2.2"` |
| Реальное устройство (локальная сеть) | `"192.168.1.100"` |
| Продакшн | `"messenger.example.com"` |

## Troubleshooting / Устранение проблем

### Все еще не работает?

1. **Проверьте сервер запущен:**
   ```bash
   cd server
   python3 main.py -d
   ```

2. **Проверьте порт открыт:**
   
   Linux:
   ```bash
   sudo ufw allow 12345
   ```
   
   Windows:
   ```
   Firewall → Inbound Rules → New Rule → Port 12345
   ```

3. **Проверьте сеть:**
   - Устройство и сервер в одной Wi-Fi сети?
   - Можете пинговать сервер с устройства?

4. **Смотрите логи сервера:**
   ```bash
   tail -f server/logs/messenger_*.log
   ```

## Дополнительная информация

Подробное руководство: [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)

## Technical Details

### What Changed
- Created `ServerConfig.kt` - centralized configuration
- Updated `LoginActivity.kt`, `ContactsActivity.kt`, `ChatActivity.kt` to use ServerConfig
- No hardcoded IPs anywhere in the codebase

### Why This Fix Works
- `10.0.2.2` is Android emulator's special alias for host machine's localhost
- Real devices don't have this alias - they need actual IP addresses
- By centralizing config, changing IP is now a one-line change
- Default value maintains emulator compatibility
