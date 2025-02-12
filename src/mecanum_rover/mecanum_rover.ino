#include <SoftwareSerial.h> 
#include <AFMotor.h>
#include <Servo.h>

SoftwareSerial BTSerial(A5, A4); //TX | RX
#define fLight A0
#define bLight A1
bool fl_status = false;
Servo myservo;
bool big_gear = false;

AF_DCMotor motor1(1);
AF_DCMotor motor2(2);
AF_DCMotor motor3(3);
AF_DCMotor motor4(4);
int motorSpeed = 100;
char command;

void setup() {
  Serial.begin(9600);           // set up Serial library at 9600 bps
  Serial.println("Motor test!");
  BTSerial.begin(9600);  
  pinMode(fLight,OUTPUT);
  pinMode(bLight,OUTPUT);
	myservo.attach(9);   
  myservo.write(90);

  // turn on motor
  motor1.setSpeed(motorSpeed);
  motor2.setSpeed(motorSpeed);
  motor3.setSpeed(motorSpeed);
  motor4.setSpeed(motorSpeed);
}


void loop() {

  command = (char)BTSerial.read(); //Read the characters from the mobile app

  if(fl_status){
    fLightOn();
  }else{
    fLightOff();
  }

  switch(command){
    case 'F':
      ForWard();
      break;

    case 'B':
      BackWard();
      bLightOn();
      break;

    case 'L':
      LeftRoll();
      break;

    case 'R':
      RightRoll();
      break;

    case 'Q':
      LeftFront();
      break;

    case 'E':
      RightFront();
      break;

    case 'Z':
      LeftBack();
      break;

    case 'X':
      Left();
      break;

    case 'Y':
      Right();
      break;

    case 'C':
      RightBack();
      break;

    case 'M':
      fl_status = !fl_status; 
      break;

    case 'S':
      Brake();
      bLightOff();
      break;

    case '0':
     myservo.write(0);
     break;
    case '1':
     myservo.write(22);
     break;
    case '2':
     myservo.write(45);
     break;
    case '3':
     myservo.write(68);
     break;
    case '4':
     myservo.write(90);
     break;
    case '5':
     myservo.write(112);
     break;
    case '6':
     myservo.write(125);
     break;
    case '7':
     myservo.write(148);
     break;
    case '8':
     myservo.write(180);
     break;
    case 'A':
      ForwardVoice();
      break;
    case 'G':
      BackwardVoice();
      break;
    case 'N':
      LeftrollVoice();
      break;
    case 'D':
      RightrollVoice();
      break;
    case 'I':
      LeftVoice();
      break;
    case 'J':
      RightVoice();
      break;
//    default:
//      Brake();
//      break;
  }   
   Serial.println(command);
  
}

void ForWard(){
  motor1.run(FORWARD);
  motor2.run(FORWARD);
  motor3.run(FORWARD);
  motor4.run(FORWARD);
}


void BackWard(){
  motor1.run(BACKWARD);
  motor2.run(BACKWARD);
  motor3.run(BACKWARD);
  motor4.run(BACKWARD);
}

void Left(){
  motor1.run(BACKWARD);
  motor2.run(BACKWARD);
  motor3.run(FORWARD);
  motor4.run(FORWARD);
}

void Right(){
  motor1.run(FORWARD);
  motor2.run(FORWARD);
  motor3.run(BACKWARD);
  motor4.run(BACKWARD);
}

void Brake(){
  motor1.run(RELEASE);
  motor2.run(RELEASE);
  motor3.run(RELEASE);
  motor4.run(RELEASE);
}

void RightRoll(){
  motor1.run(FORWARD);
  motor2.run(BACKWARD);
  motor3.run(FORWARD);
  motor4.run(BACKWARD);
}

void LeftRoll(){
  motor1.run(BACKWARD);
  motor2.run(FORWARD);
  motor3.run(BACKWARD);
  motor4.run(FORWARD);
}


void LeftBack(){
  motor1.run(BACKWARD);
  motor3.run(BACKWARD);
}

void RightBack(){
  motor2.run(BACKWARD);
  motor4.run(BACKWARD);
}

void LeftFront(){
  motor2.run(FORWARD);
  motor4.run(FORWARD);
}

void RightFront(){
  motor1.run(FORWARD);
  motor3.run(FORWARD);
}

void fLightOn(){
  digitalWrite(fLight,HIGH);
}
void fLightOff(){
  digitalWrite(fLight,LOW);
}
void bLightOn(){
  digitalWrite(bLight,HIGH);
}
void bLightOff(){
  digitalWrite(bLight,LOW);
}

void ForwardVoice(){
  motor1.run(FORWARD);
  motor2.run(FORWARD);
  motor3.run(FORWARD);
  motor4.run(FORWARD);
  delay(750);
  motor1.run(RELEASE);
  motor2.run(RELEASE);
  motor3.run(RELEASE);
  motor4.run(RELEASE);
}
void BackwardVoice(){
  motor1.run(BACKWARD);
  motor2.run(BACKWARD);
  motor3.run(BACKWARD);
  motor4.run(BACKWARD);
  delay(750);
  motor1.run(RELEASE);
  motor2.run(RELEASE);
  motor3.run(RELEASE);
  motor4.run(RELEASE);
}
void LeftVoice(){
  motor1.run(BACKWARD);
  motor2.run(BACKWARD);
  motor3.run(FORWARD);
  motor4.run(FORWARD);
  delay(400);
  motor1.run(RELEASE);
  motor2.run(RELEASE);
  motor3.run(RELEASE);
  motor4.run(RELEASE);
}

void RightVoice(){
  motor1.run(FORWARD);
  motor2.run(FORWARD);
  motor3.run(BACKWARD);
  motor4.run(BACKWARD);
  delay(400);
  motor1.run(RELEASE);
  motor2.run(RELEASE);
  motor3.run(RELEASE);
  motor4.run(RELEASE);
}

void RightrollVoice(){
  motor1.run(FORWARD);
  motor2.run(BACKWARD);
  motor3.run(FORWARD);
  motor4.run(BACKWARD);
  delay(700);
  motor1.run(RELEASE);
  motor2.run(RELEASE);
  motor3.run(RELEASE);
  motor4.run(RELEASE);
}

void LeftrollVoice(){
  motor1.run(BACKWARD);
  motor2.run(FORWARD);
  motor3.run(BACKWARD);
  motor4.run(FORWARD);
  delay(700);
  motor1.run(RELEASE);
  motor2.run(RELEASE);
  motor3.run(RELEASE);
  motor4.run(RELEASE);
}
