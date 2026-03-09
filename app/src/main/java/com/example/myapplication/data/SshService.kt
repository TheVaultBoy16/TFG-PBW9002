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
        privateKey: String,
        command: String,
        port: Int = 22,
        passphrase: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            var session: Session? = null
            var channel: ChannelExec? = null
            try {
                // Limpiamos identidades previas para evitar conflictos
                jsch.removeAllIdentity()
                
                // Añadimos la identidad (clave privada)
                // convertimos el String de la clave a un array de bytes
                jsch.addIdentity("id_rsa", privateKey.toByteArray(), null, passphrase?.toByteArray())

                session = jsch.getSession(username, hostname, port)
                
                val config = Properties()
                config["StrictHostKeyChecking"] = "no"
                // Forzamos el uso de autenticación por clave pública
                config["PreferredAuthentications"] = "publickey"
                session.setConfig(config)
                
                session.connect(5000)

                channel = session.openChannel("exec") as ChannelExec
                val fullCommand = "export LC_ALL=C; export PATH=\$PATH:/usr/bin:/usr/sbin:/snap/bin; $command"
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
                    val errorMsg = errorResult.ifEmpty { result }.ifEmpty { "Error code ${channel.exitStatus}" }
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
