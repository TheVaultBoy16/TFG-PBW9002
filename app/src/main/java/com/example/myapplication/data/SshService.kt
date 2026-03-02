package com.example.myapplication.data

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties

class SshService {

    private val jsch = JSch()

    suspend fun executeCommand(
        username: String, 
        hostname: String, 
        password: String, 
        command: String,
        port: Int = 22 // Valor por defecto del puerto
    ): String {
        return withContext(Dispatchers.IO) {
            var session: Session? = null
            var channel: ChannelExec? = null
            try {
                session = jsch.getSession(username, hostname, port)
                session.setPassword(password)
                
                val config = Properties()
                config["StrictHostKeyChecking"] = "no"
                session.setConfig(config)
                
                session.connect(5000)

                channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(command)
                
                val inputStream: InputStream = channel.inputStream
                channel.connect()

                val result = inputStream.bufferedReader().use { it.readText() }
                result
            } catch (e: Exception) {
                "Error: ${e.message}"
            } finally {
                channel?.disconnect()
                session?.disconnect()
            }
        }
    }
}
