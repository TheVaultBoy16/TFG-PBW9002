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
        port: Int = 22
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

                channel.setCommand("export PATH=\$PATH:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/snap/bin; $command")
                
                val inputStream: InputStream = channel.inputStream
                val errorStream: InputStream = channel.errStream
                
                channel.connect()

                val result = inputStream.bufferedReader().use { it.readText() }
                val errorResult = errorStream.bufferedReader().use { it.readText() }

                // Esperamos a que el canal se cierre para obtener el estado de salida
                while (!channel.isClosed) {
                    Thread.sleep(100)
                }

                if (channel.exitStatus != 0 && result.isEmpty()) {
                    "Error: ${errorResult.ifEmpty { "Código de salida ${channel.exitStatus}" }}"
                } else {
                    result
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            } finally {
                channel?.disconnect()
                session?.disconnect()
            }
        }
    }
}
