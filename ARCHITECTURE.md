# Architecture and Fix Overview

## Before the Fix - The Problem

```
┌─────────────────────────────────────────────────────────────┐
│                    Android Emulator                          │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ LoginActivity│  │ContactsActiv.│  │ ChatActivity │      │
│  │              │  │              │  │              │      │
│  │ connect(     │  │ connect(     │  │ connect(     │      │
│  │ "10.0.2.2",  │  │ "10.0.2.2",  │  │ "10.0.2.2",  │ x3   │
│  │  12345)      │  │  12345)      │  │  12345)      │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                 │                 │               │
│         │  10.0.2.2 = localhost on host    │               │
│         └─────────────────┼─────────────────┘               │
│                           │                                 │
└───────────────────────────┼─────────────────────────────────┘
                            ▼
                  ┌─────────────────┐
                  │  Host Machine   │
                  │  (localhost)    │
                  │                 │
                  │  Server:12345   │
                  └─────────────────┘
                         ✅ WORKS


┌─────────────────────────────────────────────────────────────┐
│                    Real Android Device                       │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ LoginActivity│  │ContactsActiv.│  │ ChatActivity │      │
│  │              │  │              │  │              │      │
│  │ connect(     │  │ connect(     │  │ connect(     │      │
│  │ "10.0.2.2",  │  │ "10.0.2.2",  │  │ "10.0.2.2",  │ x3   │
│  │  12345)  ❌  │  │  12345)  ❌  │  │  12345)  ❌  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│         │                 │                 │               │
│         │  10.0.2.2 doesn't exist!         │               │
│         └─────────────────X─────────────────┘               │
│                      CONNECTION FAILED                      │
│                      💥 APP CRASHES                         │
└─────────────────────────────────────────────────────────────┘

                  ┌─────────────────┐
                  │  Server         │
                  │  192.168.1.100  │
                  │                 │
                  │  Port: 12345    │
                  └─────────────────┘
                    (unreachable)
```

## After the Fix - The Solution

```
┌──────────────────────────────────────────────────────────────┐
│                 Centralized Configuration                     │
│                                                              │
│    ┌─────────────────────────────────────────────────┐      │
│    │  ServerConfig.kt                                 │      │
│    │                                                  │      │
│    │  object ServerConfig {                          │      │
│    │    const val SERVER_HOST = "192.168.1.100"     │      │
│    │    const val SERVER_PORT = 12345                │      │
│    │  }                                              │      │
│    └─────────────────────────────────────────────────┘      │
│                           ▲                                  │
│              ┌────────────┼────────────┐                     │
│              │            │            │                     │
│  ┌───────────┴────┐  ┌───┴──────────┐ ┌┴──────────────┐    │
│  │ LoginActivity  │  │ContactsActiv.│ │ ChatActivity  │    │
│  │                │  │              │ │               │    │
│  │ connect(       │  │ connect(     │ │ connect(      │x3  │
│  │ ServerConfig.  │  │ ServerConfig.│ │ ServerConfig. │    │
│  │ SERVER_HOST,   │  │ SERVER_HOST, │ │ SERVER_HOST,  │    │
│  │ SERVER_PORT)   │  │ SERVER_PORT) │ │ SERVER_PORT)  │    │
│  └────────┬───────┘  └──────┬───────┘ └───────┬───────┘    │
│           │                 │                 │             │
└───────────┼─────────────────┼─────────────────┼─────────────┘
            │                 │                 │
            │   192.168.1.100:12345            │
            └─────────────────┼─────────────────┘
                             ▼
                  ┌─────────────────┐
                  │  Server         │
                  │  192.168.1.100  │
                  │  Port: 12345    │
                  └─────────────────┘
                         ✅ WORKS
```

## Configuration for Different Scenarios

### Emulator
```kotlin
object ServerConfig {
    const val SERVER_HOST = "10.0.2.2"  // Special emulator address
    const val SERVER_PORT = 12345
}
```

### Real Device (Local Network)
```kotlin
object ServerConfig {
    const val SERVER_HOST = "192.168.1.100"  // Server's local IP
    const val SERVER_PORT = 12345
}
```

### Production
```kotlin
object ServerConfig {
    const val SERVER_HOST = "messenger.example.com"  // Domain name
    const val SERVER_PORT = 12345
}
```

## Data Flow

```
┌─────────────────┐
│  LoginActivity  │
│                 │
│  1. User enters │
│     credentials │
│                 │
│  2. Connect to  │
│     server      │──────┐
│                 │      │
│  3. Send AUTH   │      │
│     command     │      │
│                 │      │
│  4. Receive     │      │
│     AUTH_OK     │      │
│                 │      │
│  5. Navigate to │      │
│     Contacts    │      │
└────────┬────────┘      │
         │               │
         ▼               │
┌─────────────────┐      │
│ContactsActivity │      │
│                 │      │
│  1. Connect to  │──────┤  All use ServerConfig
│     server      │      │  ✅ Single point of config
│                 │      │  ✅ Easy to change
│  2. Send        │      │  ✅ Type-safe
│     LIST_USERS  │      │
│                 │      │
│  3. Display     │      │
│     contacts    │      │
└────────┬────────┘      │
         │               │
         ▼               │
┌─────────────────┐      │
│  ChatActivity   │      │
│                 │      │
│  1. Connect to  │──────┤
│     server      │      │
│                 │      │
│  2. Send/receive│      │
│     messages    │      │
│                 │      │
│  3. Poll for    │──────┘
│     updates     │
└─────────────────┘
```

## Network Communication

```
Client (Android App)                     Server (Python)
─────────────────────                   ─────────────────

┌─────────────────┐                     ┌─────────────────┐
│  SocketManager  │                     │   main.py       │
│                 │                     │                 │
│  SSL Socket     │◄───────TLS──────────►│  SSL Socket    │
│                 │      Connection      │                 │
└─────────────────┘                     └─────────────────┘
         │                                       │
         │  AUTH:username:password              │
         │──────────────────────────────────────►│
         │                                       │
         │◄──────AUTH_OK:user_id─────────────────│
         │                                       │
         │  LIST_USERS                          │
         │──────────────────────────────────────►│
         │                                       │
         │◄──────USERS:[{...}]───────────────────│
         │                                       │
         │  SEND_MSG:{...}                      │
         │──────────────────────────────────────►│
         │                                       │
         │◄──────MSG_SENT:msg_id─────────────────│
         │                                       │
```

## File Structure

```
CorporateMessenger/
├── app/
│   └── src/main/java/com/example/messenger/
│       ├── network/
│       │   ├── ServerConfig.kt        ✨ NEW - Centralized config
│       │   ├── SocketManager.kt       (unchanged)
│       │   └── FileUploader.kt        (unchanged)
│       ├── ui/
│       │   ├── LoginActivity.kt       ✏️  UPDATED - uses ServerConfig
│       │   ├── ContactsActivity.kt    ✏️  UPDATED - uses ServerConfig
│       │   └── ChatActivity.kt        ✏️  UPDATED - uses ServerConfig
│       └── data/
│           └── ...                    (unchanged)
├── server/
│   ├── main.py                        (unchanged)
│   └── ...
├── README.md                          ✏️  UPDATED - new instructions
├── DEPLOYMENT_GUIDE.md                ✨ NEW - deployment help
├── QUICKFIX.md                        ✨ NEW - quick reference
├── CHANGES_SUMMARY.md                 ✨ NEW - technical summary
└── ARCHITECTURE.md                    ✨ NEW - this file
```

## Benefits of This Architecture

1. **Single Point of Configuration**
   - Change server address in ONE place
   - No need to search through multiple files

2. **Type Safety**
   - Compile-time constants
   - No typos in IP addresses

3. **Clear Documentation**
   - Comments explain each configuration option
   - Easy for new developers to understand

4. **Environment Flexibility**
   - Easy to switch between emulator/device/production
   - Just change one constant

5. **Backward Compatible**
   - Default value maintains emulator functionality
   - Existing workflows unchanged

6. **Maintainable**
   - Clear separation of concerns
   - Configuration separate from business logic

## Migration Steps

For existing deployments:

```
1. Pull latest code
   git pull origin main

2. Edit ServerConfig.kt
   Change SERVER_HOST to your server IP

3. Rebuild
   Build → Clean Project
   Build → Rebuild Project

4. Deploy
   Run → Run 'app'
```

## Testing Matrix

| Environment | SERVER_HOST | Expected Result |
|-------------|-------------|-----------------|
| Emulator | `10.0.2.2` | ✅ Works |
| Device (same network) | `192.168.1.100` | ✅ Works |
| Device (different network) | `10.0.2.2` | ❌ Fails |
| Device (production) | `messenger.example.com` | ✅ Works |

## Troubleshooting Flow

```
App crashes after login?
     │
     ├─► Check ServerConfig.kt
     │   └─► Is SERVER_HOST correct for your environment?
     │       ├─► Emulator: use "10.0.2.2"
     │       ├─► Device: use server's IP (e.g., "192.168.1.100")
     │       └─► Production: use domain/public IP
     │
     ├─► Check Server Status
     │   └─► Is server running? python3 main.py -d
     │
     ├─► Check Network
     │   └─► Device and server on same network?
     │       └─► Can ping server from device?
     │
     └─► Check Firewall
         └─► Is port 12345 open?
```

## Security Considerations

- All connections use SSL/TLS
- Passwords hashed with salt (SHA-256)
- No credentials in code
- Network security config allows user certificates (debug only)
- Release builds should use trusted CA certificates
