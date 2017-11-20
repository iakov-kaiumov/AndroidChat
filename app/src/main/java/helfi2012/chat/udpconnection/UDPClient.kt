package helfi2012.chat.udpconnection

import helfi2012.chat.utils.Constants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class UDPClient(private val port: Int, host: String) {
    private var socket = DatagramSocket()
    private val address: InetAddress = InetAddress.getByName(host)
    var onAudioReceive: (bytes: ByteArray) -> Unit = {}
    var onImageReceive: (bytes: ByteArray) -> Unit = {}

    fun close() = socket.close()

    fun sendByteArray(array: ByteArray, state: Int) = try {
        val sendingBuffer = ByteArray(UDP_PACKET_FULL_SIZE)
        when (state) {
            AUDIO_STATE -> {
                sendingBuffer[0] = AUDIO_TAG
                sendingBuffer[1] = (array.size / 100).toByte()
                sendingBuffer[2] = (array.size % 100).toByte()
                System.arraycopy(array, 0, sendingBuffer, 3, array.size)
                socket.send(DatagramPacket(sendingBuffer, sendingBuffer.size, address, port))
            }
            VIDEO_STATE -> {
                val localBuffer = ByteArray(UDP_PACKET_FULL_SIZE - 3)
                val inputStream = ByteArrayInputStream(array)
                while (true) {
                    val bytesRead = inputStream.read(localBuffer)
                    if (bytesRead == -1) break
                    sendingBuffer[0] = VIDEO_START_TAG
                    sendingBuffer[1] = (bytesRead / 100).toByte()
                    sendingBuffer[2] = (bytesRead % 100).toByte()
                    System.arraycopy(localBuffer, 0, sendingBuffer, 3, bytesRead)
                    socket.send(DatagramPacket(sendingBuffer, sendingBuffer.size, address, port))
                }
                sendingBuffer[0] = VIDEO_STOP_TAG
                if (socket.isClosed) socket = DatagramSocket()
                socket.send(DatagramPacket(sendingBuffer, sendingBuffer.size, address, port))
            }
            else -> Unit
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    private fun newDatagramInstance(): DatagramSocket {
        val socket: DatagramSocket?
        socket = try {
            DatagramSocket(Constants.UDP_PORT)
        } catch (e: Exception) {
            newDatagramInstance()
        }
        return socket
    }

    var receiveThread = object : Thread() {
        override fun run() {
            val socket = newDatagramInstance()
            val videoStream = ByteArrayOutputStream()
            while (!Thread.currentThread().isInterrupted) {
                val data = ByteArray(UDP_PACKET_FULL_SIZE)
                val packet = DatagramPacket(data, data.size)
                try {
                    socket.soTimeout = 200
                    socket.receive(packet)
                    when {
                        packet.data[0] == VIDEO_START_TAG -> {
                            val size = packet.data[1].toInt()*100 + packet.data[2]
                            val bytes = ByteArray(size)
                            System.arraycopy(data, 3, bytes, 0, size)
                            videoStream.write(bytes)
                            videoStream.flush()
                        }
                        packet.data[0] == VIDEO_STOP_TAG -> {
                            val bytes = videoStream.toByteArray()
                            onImageReceive.invoke(bytes)
                            videoStream.reset()
                        }
                        packet.data[0] == AUDIO_TAG -> {
                            val size = packet.data[1].toInt()*100 + packet.data[2]
                            val bytes = ByteArray(size)
                            System.arraycopy(data, 3, bytes, 0, size)
                            onAudioReceive.invoke(bytes)
                        }
                    }
                } catch (e: SocketTimeoutException) {
                }
            }
            socket.close()
        }
    }

    companion object {
        private val UDP_PACKET_FULL_SIZE = 4000
        private val VIDEO_START_TAG: Byte = 5
        private val VIDEO_STOP_TAG: Byte = 10
        private val AUDIO_TAG: Byte = 20
        val AUDIO_STATE = 1
        val VIDEO_STATE = 2
    }
}