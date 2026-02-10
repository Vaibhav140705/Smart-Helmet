#include <AltSoftSerial.h>

AltSoftSerial BT;   // RX = 8, TX = 9

void setup() {
  BT.begin(9600);
  delay(1000);      // let HC-05 stabilize
}

void loop() {
  BT.println("CRASH");
  delay(3000);
}
