from bluetooth import *
import w1thermsensor
import RPi.GPIO as GPIO

GPIO.setmode(GPIO.BCM)
GPIO.setup(21, GPIO.OUT)
GPIO.setwarnings(False)
GPIO.output(21, 0)

sensor = w1thermsensor.W1ThermSensor()
sensor.set_precision(10)

server_sock = BluetoothSocket(RFCOMM)
server_sock.bind(("", PORT_ANY))
server_sock.listen(1)

port = server_sock.getsockname()[1]

uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

advertise_service(server_sock, "TempStationServer",
                 service_id=uuid,
                 service_classes=[uuid, SERIAL_PORT_CLASS],
                  profiles=[SERIAL_PORT_PROFILE],
                  )

client_sock, client_info = server_sock.accept()
print("Waiting for RFCOMM channel: ", port)
print("Accepted connection from: ", client_info)
GPIO.output(21, 1)
while True:
    try:
        data = sensor.get_temperature()
        print("Sending temperature data: {}".format(data))
        client_sock.send("{}\n".format(data))
    except IOError:
        pass
    except KeyboardInterrupt:
        print("Ending")
        GPIO.output(21, 0)
        GPIO.cleanup()
        client_sock.close()
        server_sock.close()
        print("Finished")
        break
