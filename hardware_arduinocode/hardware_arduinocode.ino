#include <BluetoothSerial.h>
#include <Wire.h>
#include <MPU6050.h>



BluetoothSerial BT;

// Pin definitions

#define TRIG_PIN    5
#define ECHO_PIN    18
#define BUZZER_PIN  13

unsigned long crashTime = 0;
bool waitingForCancel = false;
unsigned long lastSend = 0;

// Parameters

int beepFreq = 2000;
float crashThreshold = 2.0;   // ✅ 2.0G crash threshold

MPU6050 mpu;
bool crashSent = false;       // prevent repeated CRASH spam


void setup() {
  Serial.begin(9600);
  BT.begin("ESP_32_Bl");             // HC-05 default baud
  delay(1000);                // let HC-05 stabilize

  BT.println("BT Ready");

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  pinMode(BUZZER_PIN, OUTPUT);

  Wire.begin();
  mpu.initialize();
}


void crashBeep() {
  for (int i = 0; i < 3; i++) {
    tone(BUZZER_PIN, 1200);
    delay(300);
    noTone(BUZZER_PIN);
    delay(100);
  }
}

void loop() {

  // READ ULTRASONIC SENSOR
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000);
  float distance = (duration > 0) ? (duration * 0.0343 / 2) : -1;

 
  // READ MPU6050

  int16_t ax, ay, az;
  mpu.getAcceleration(&ax, &ay, &az);

  float accelX = ax / 16384.0;
  float accelY = ay / 16384.0;
  float accelZ = az / 16384.0;

  float totalG = sqrt(
    accelX * accelX +
    accelY * accelY +
    accelZ * accelZ
  );

 
  // CRASH DETECTION
 
  if (totalG >= crashThreshold && !waitingForCancel) {
    BT.println("CRASH");
    crashBeep();
    crashTime = millis();     // Start the timer
    waitingForCancel = true;  // Enter "Waiting Mode"
}

// 2. THE 10-SECOND WAIT LOGIC (Non-blocking)
if (waitingForCancel) {
    // Check for Bluetooth Cancel Message
    if (BT.available()) {
        String msg = BT.readStringUntil('\n');
        msg.trim();
        if (msg == "CANCEL") {
            BT.println("CANCELLED");
            waitingForCancel = false; // Reset system for next crash
        }
    }

    // Check if 10 seconds have passed
    if (waitingForCancel && (millis() - crashTime > 10000)) {
        BT.println("SOS");
        waitingForCancel = false; // Reset system after sending SOS
    }
    
    // NOTE: Because there is no "return" or "while" here, 
    // the code continues below to the proximity beeping logic!
}


  // REVERSE SENSOR LOGIC

  if (distance < 0 || distance > 40) {
    noTone(BUZZER_PIN);
  }
  else if (distance > 25) {
    tone(BUZZER_PIN, beepFreq);
    delay(200);
    noTone(BUZZER_PIN);
    delay(300);
  }
  else if (distance > 15) {
    tone(BUZZER_PIN, beepFreq);
    delay(150);
    noTone(BUZZER_PIN);
    delay(150);
  }
  else if (distance > 8) {
    tone(BUZZER_PIN, beepFreq);
    delay(100);
    noTone(BUZZER_PIN);
    delay(80);
  }
  else {
    tone(BUZZER_PIN, beepFreq);  // continuous
  }


  // HEARTBEAT (every 2 sec)

  static unsigned long lastSend = 0;
  if (millis() - lastSend >= 2000) {
    BT.println("OK");
    lastSend = millis();
  }
}