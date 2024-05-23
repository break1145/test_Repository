import random
import threading
import socket
from crcmod.predefined import Crc

HOST = '127.0.0.1'
PORT = 12345
seq_num_lock = threading.Lock()  # 用于保护全局变量 seq_num 的锁
LISTEN_PORT = 11451


def get_crc(data):
    crc32_func = Crc('crc-32')
    crc32_func.update(data.encode())
    return crc32_func.hexdigest()


class Sender:
    def __init__(self, host, port, buffer_size=1024, window_size=5, timeout=1.0):
        self.host = host
        self.port = port
        self.buffer_size = buffer_size # 缓冲区
        self.timeout = timeout
        self.window_size = window_size
        self.listen_port = LISTEN_PORT # 监听端口
        self.sent_packets = {} # 已发送数据

    def send_data(self, data, addr):
        global seq_num_lock
        global seq_num
        global error_rate
        sender_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        with seq_num_lock:  # 加锁保护对 seq_num 的操作
            crc = get_crc(data)
            packet = f"{crc}:{seq_num}:{data}"
            self.sent_packets[seq_num] = packet
            
            # 模拟 一定概率发送错误数据
            if (random.randint(0, 100) / 100) < error_rate:
                packet = f"WRONG_CRC:{seq_num}:{data}"
            seq_num += 1
            sender_socket.sendto(packet.encode(), addr)
            print(f"Send data [{packet}] to [{addr[0]}:{addr[1]}]\n")
        sender_socket.close()

    def listen_for_resend(self):
        listen_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        listen_socket.bind((self.host, self.listen_port))
        while True:
            data, addr = listen_socket.recvfrom(self.buffer_size)
            # 收到NAK 重发对应数据
            if data.decode().startswith("NAK"):
                _, seq_to_resend = data.decode().split(":")
                print(f"Received NAK:{seq_to_resend} from [{addr[0]}:{addr[1]}]")
                # print("@@@" + seq_to_resend + self.sent_packets.__str__())
                seq_to_resend = int(seq_to_resend)

                if (seq_to_resend) in self.sent_packets:
                    resend_packet = self.sent_packets[(seq_to_resend)]
                    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sender_socket:
                        sender_socket.sendto(resend_packet.encode(), (self.host, self.port))
                        print(f"Resend data [{resend_packet}] to [{self.host}:{self.port}]\n")
            # ACK 在已发送中清除对应数据
            if data.decode().startswith("ACK"):
                _, seq_acked = data.decode().split(":")
                if seq_acked in self.sent_packets:
                    del self.sent_packets[seq_acked]
                    print(f"ACK received for seq [{seq_acked}], packet cleared.")


class Receiver:
    def __init__(self, host, port, buffer_size=1024, timeout=1.0):
        self.host = host
        self.port = port
        self.buffer_size = buffer_size
        self.timeout = timeout
        self.val_set = {}

    def recv_data(self, addr):
        receiver_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        receiver_socket.bind(addr)
        while True:

            data, addr = receiver_socket.recvfrom(self.buffer_size)
            crc, seq, packet_data = data.decode().split(':', 2)

            # 向监听端口发送ACK/NAK的socket
            sender_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            resend_addr = (addr[0], 11451)

            if crc == get_crc(packet_data):  # 验证 CRC32
                print(f"Receive data [{packet_data}] from [{addr[0]}:{addr[1]}]\n")
                self.val_set[seq] = True
                sender_socket.sendto(f"ACK:{seq}".encode(), resend_addr)
                print(f"Send ACK{seq} to [{resend_addr[0]}:{resend_addr[1]}]\n")
            else:
                print(f"Received corrupted data from {addr}: {packet_data}")
                sender_socket.sendto(f"NAK:{seq}".encode(), resend_addr)
                print(f"Send NAK{seq} to [{resend_addr[0]}:{resend_addr[1]}]\n")
            sender_socket.close()


if __name__ == '__main__':
    sender = Sender(HOST, PORT)
    receiver = Receiver(HOST, PORT)
    seq_num = 0
    error_rate = 0.5

    recv_thread = threading.Thread(target=receiver.recv_data, args=((HOST, PORT),))
    recv_thread.start()
    listen_thread = threading.Thread(target=sender.listen_for_resend, args=())
    listen_thread.start()

    for i in range(5):
        data = f"Data {i}"
        with seq_num_lock:  # 确保 send 和 recv 交替输出
            send_thread = threading.Thread(target=sender.send_data, args=(data, (HOST, PORT)))
            send_thread.start()
