import socket
import ssl
import json


class Weather:
    def __init__(self, api):
        self.api = api

    def get_weather(self, city):
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        # 创建socket并用SSL包装
        context = ssl.create_default_context()
        wrapped_sock = context.wrap_socket(sock, server_hostname="api.openweathermap.org")
        wrapped_sock.connect(("api.openweathermap.org", 443))

        # 创建get请求
        request = f"GET /data/2.5/weather?q={city}&appid={self.api}&units=metric HTTP/1.1\r\n"
        request += "Host: api.openweathermap.org\r\n"
        request += "Connection: close\r\n"
        request += "\r\n"
        wrapped_sock.send(request.encode())

        response = b""
        while True:
            data = wrapped_sock.recv(1024)
            if not data:
                break
            response += data
        wrapped_sock.close()
        # print(response.decode())

        # 响应分割为头和体
        headers, body = response.split(b"\r\n\r\n", 1)
        weather_data = json.loads(body.decode("utf-8"))
        if weather_data.get("main"):
            temperature = weather_data["main"]["temp"]
            weather_description = weather_data["weather"][0]["description"]
            print(f"{city}温度: {temperature}°C")
            print(f"天气情况: {weather_description}")
        else:
            print("获取天气数据时出错:", weather_data)


if __name__ == '__main__':
    api_key = 'ccf2e20ab98654d38ebe299afa2ce560'
    test1 = Weather(api_key)
    test1.get_weather('Tokyo')
