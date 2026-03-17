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
        port: Int = 22
    ): String {
        return withContext(Dispatchers.IO) {
            var session: Session? = null
            var channel: ChannelExec? = null
            try {
                jsch.removeAllIdentity()
                jsch.addIdentity("id_rsa", privateKey.toByteArray(), null, null)

                session = jsch.getSession(username, hostname, port)
                
                val config = Properties()
                config["StrictHostKeyChecking"] = "no"
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

    suspend fun downloadBytes(
        username: String,
        hostname: String,
        privateKey: String,
        remotePath: String,
        port: Int = 22,
        useSudo: Boolean = false
    ): ByteArray? {
        return withContext(Dispatchers.IO) {
            var session: Session? = null
            try {
                jsch.removeAllIdentity()
                jsch.addIdentity("id_rsa", privateKey.toByteArray(), null, null)
                session = jsch.getSession(username, hostname, port)
                val config = Properties()
                config["StrictHostKeyChecking"] = "no"
                config["PreferredAuthentications"] = "publickey"
                session.setConfig(config)
                session.connect(5000)

                val channel = session.openChannel("exec") as ChannelExec
                val cmd = if (useSudo) "sudo -n cat \"$remotePath\"" else "cat \"$remotePath\""
                channel.setCommand(cmd)
                val inputStream = channel.inputStream
                channel.connect()

                val bytes = inputStream.readBytes()
                
                while (!channel.isClosed) {
                    Thread.sleep(50)
                }
                
                if (channel.exitStatus == 0) bytes else null
            } catch (e: Exception) {
                null
            } finally {
                session?.disconnect()
            }
        }
    }
}
