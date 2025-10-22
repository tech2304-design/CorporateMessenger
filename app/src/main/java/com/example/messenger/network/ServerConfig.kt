package com.example.messenger.network

/**
 * Centralized server configuration
 * 
 * For emulator testing: use "10.0.2.2"
 * For real device testing: replace with actual server IP address (e.g., "192.168.1.100")
 * For production: use your server's domain name or IP address
 */
object ServerConfig {
    /**
     * Server host address
     * 
     * IMPORTANT: Change this value when deploying to real devices!
     * - Emulator: "10.0.2.2" (localhost of host machine)
     * - Real device on same network: Local IP of server (e.g., "192.168.1.100")
     * - Production: Server domain or public IP (e.g., "messenger.example.com" or "203.0.113.10")
     */
    const val SERVER_HOST = "10.0.2.2"
    
    /**
     * Server port
     */
    const val SERVER_PORT = 12345
}
