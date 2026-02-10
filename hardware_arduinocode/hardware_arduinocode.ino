#include <AltSoftSerial.h>
#include <Wire.h>
#include <MPU6050.h>

// --------------------
// Bluetooth (AltSoftSerial)
// RX = 8, TX = 9 (fixed)
// --------------------
AltSoftSerial BT;

// --------------------
// Pin definitions
// --------------------
#define TRIG_PIN    10
#define ECHO_PIN    11
#define BUZZER_PIN  6   // 🔔 moved from 9 → 6

// --------------------
// Parameters
// --------------------
int beepFreq = 2000;
float crashThreshold = 2.0;   // ✅ 2.0G crash threshold

MPU6050 mpu;
bool crashSent = false;       // prevent repeated CRASH spam

// --------------------
void setup() {
  Serial.begin(9600);
  BT.begin(9600);             // HC-05 default baud
  delay(1000);                // let HC-05 stabilize

  BT.println("BT Ready");

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  pinMode(BUZZER_PIN, OUTPUT);

  Wire.begin();
  mpu.initialize();
}

// --------------------
void crashBeep() {
  for (int i = 0; i < 3; i++) {
    tone(BUZZER_PIN, 1200);
    delay(300);
    noTone(BUZZER_PIN);
    delay(100);
  }
}

// --------------------
void loop() {

  // =========================
  // READ ULTRASONIC SENSOR
  // =========================
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000);
  float distance = (duration > 0) ? (duration * 0.0343 / 2) : -1;

  // =========================
  // READ MPU6050
  // =========================
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

  // =========================
  // CRASH DETECTION
  // =========================
  if (totalG >= crashThreshold && !crashSent) {
    crashSent = true;

    BT.println("CRASH");
    crashBeep();

    unsigned long start = millis();
    bool cancelled = false;

    // ⏱ 10 second cancel window
    while (millis() - start < 10000) {
      if (BT.available()) {
        String msg = BT.readStringUntil('\n');
        msg.trim();

        if (msg == "CANCEL") {
          cancelled = true;
          BT.println("CANCELLED");
          break;
        }
      }
    }

    if (!cancelled) {
      BT.println("SOS");
    }

    return;   // stop rest of loop after crash
  }

  // =========================
  // REVERSE SENSOR LOGIC
  // =========================
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

  // =========================
  // HEARTBEAT (every 2 sec)
  // =========================
  static unsigned long lastSend = 0;
  if (millis() - lastSend >= 2000) {
    BT.println("OK");
    lastSend = millis();
  }
}
