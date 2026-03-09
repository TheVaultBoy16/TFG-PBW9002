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
                
                // Forzamos el uso de un shell para asegurar que el PATH y el entorno sean correctos.

                val fullCommand = "source /etc/profile; export PATH=\$PATH:/usr/bin:/usr/sbin:/snap/bin; $command"
                channel.setCommand(fullCommand)
                
                val inputStream: InputStream = channel.inputStream
                val errorStream: InputStream = channel.errStream
                
                channel.connect()

                val result = inputStream.bufferedReader().use { it.readText() }
                val errorResult = errorStream.bufferedReader().use { it.readText() }

                while (!channel.isClosed) {
                    Thread.sleep(50)
                }

                if (channel.exitStatus != 0) {
                    val errorMsg = errorResult.ifEmpty { result }.ifEmpty { "Error desconocido (code ${channel.exitStatus})" }
                    "ERROR_SSH: $errorMsg"
                } else {
                    result
                }
            } catch (e: Exception) {
                "ERROR_SSH: ${e.message}"
            } finally {
                channel?.disconnect()
                session?.disconnect()
            }
        }
    }
}
