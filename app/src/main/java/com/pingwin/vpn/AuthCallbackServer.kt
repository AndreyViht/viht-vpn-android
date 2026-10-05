package com.pingwin.vpn

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket

object AuthCallbackServer {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    const val PORT = 45123

    fun start(onAuthReceived: (token: String, subToken: String, tgId: String, email: String) -> Unit) {
        stop()
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = ServerSocket(PORT, 10, InetAddress.getByName("127.0.0.1"))
                serverSocket = socket

                while (!socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (e: Exception) {
                        break
                    }

                    launch(Dispatchers.IO) {
                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            val reqLine = reader.readLine() ?: return@launch
                            val parts = reqLine.split(" ")

                            if (parts.size >= 2 && parts[1].contains("/auth/callback")) {
                                val uri = Uri.parse("http://127.0.0.1" + parts[1])
                                val token = uri.getQueryParameter("token") ?: ""
                                val subToken = uri.getQueryParameter("sub_token") ?: ""
                                val tgId = uri.getQueryParameter("tg_id") ?: ""
                                val email = uri.getQueryParameter("email") ?: ""

                                val html = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                      <meta charset="utf-8">
                                      <title>Viht VPN</title>
                                      <meta name="viewport" content="width=device-width, initial-scale=1">
                                      <script>
                                        try { window.location.href = "vihtvpn://auth?token=$token&sub_token=$subToken&tg_id=$tgId&email=$email"; } catch(e){}
                                        setTimeout(function() {
                                          try { window.close(); } catch(e){}
                                        }, 1200);
                                      </script>
                                    </head>
                                    <body style="background:#07090E;color:#00D2FF;font-family:system-ui,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;padding:20px;">
                                      <div>
                                        <h2 style="color:#00FF88;margin-bottom:8px;">Авторизация успешна!</h2>
                                        <p style="color:#A0AEC0;font-size:14px;">Возвращаемся в приложение Viht VPN...</p>
                                      </div>
                                    </body>
                                    </html>
                                """.trimIndent()

                                val writer = OutputStreamWriter(client.getOutputStream(), "UTF-8")
                                writer.write("HTTP/1.1 200 OK\r\n")
                                writer.write("Content-Type: text/html; charset=utf-8\r\n")
                                writer.write("Access-Control-Allow-Origin: *\r\n")
                                writer.write("Access-Control-Allow-Private-Network: true\r\n")
                                writer.write("Content-Length: ${html.toByteArray(Charsets.UTF_8).size}\r\n")
                                writer.write("Connection: close\r\n\r\n")
                                writer.write(html)
                                writer.flush()

                                onAuthReceived(token, subToken, tgId, email)
                            } else {
                                val writer = OutputStreamWriter(client.getOutputStream(), "UTF-8")
                                writer.write("HTTP/1.1 204 No Content\r\n")
                                writer.write("Access-Control-Allow-Origin: *\r\n")
                                writer.write("Access-Control-Allow-Private-Network: true\r\n")
                                writer.write("Connection: close\r\n\r\n")
                                writer.flush()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            try { client.close() } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }
}
