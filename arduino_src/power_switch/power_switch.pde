#define SO 12
#define SCK 13

#define CS 11  // to CS Pin of MAX6675

#define RELAY_PIN 7

int TEMP_FUDGE_FACTOR = 0;
int incomingByte = 0;
boolean deviceOn = false;

void setup() {

  pinMode(SO, INPUT);
  pinMode(SCK, OUTPUT);

  pinMode(CS, OUTPUT);
  digitalWrite(CS, HIGH);  // Disable device

  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);
 
  Serial.begin(9600);
}

unsigned int readTemp(int pin, int error, int samples) {
  unsigned int value = 0;
  int errorVal;
  float temp;
  unsigned int tempOut;
  
  for (int i = samples; i > 0; i--) {
    digitalWrite(pin,LOW); // Enable device

    digitalWrite(SCK, HIGH);
    digitalWrite(SCK, LOW);

    /* See the datasheet on the MAX6675 for how to read the SPI data that gets spit out.  */
    for (int j = 11; j >= 0; j--) {
	  digitalWrite(SCK, HIGH);  
	  value += digitalRead(SO) << j;
	  digitalWrite(SCK, LOW);
    }
  
    /* check for error */
    digitalWrite(SCK, HIGH);
    errorVal = digitalRead(SO); 
    digitalWrite(SCK, LOW);
  
    digitalWrite(pin, HIGH); //Disable Device
  }
  
  value = value/samples;  // Divide the value by the number of samples to get the average
  
  value = value + error;  // Insert the calibration error value
  
  temp = ((value * 0.25) * (9.0 / 5.0)) + 32.0;

  tempOut = temp;
  
  if(error != 0) { 
    return 9999;
  } 
  
  return tempOut;
}

void loop() {
  
  if (Serial.available() > 0) {
    // read the incoming byte:
    incomingByte = Serial.read();
    
    if(incomingByte == 49) {  // read a '1'
      if(!deviceOn) { // turn device on
        digitalWrite(RELAY_PIN, HIGH);
      }
      
      deviceOn = true;
    }
    else if(incomingByte == 48) { // read a '0'
      if(deviceOn) { // turn device off
        digitalWrite(RELAY_PIN, LOW);
      }
      
      deviceOn = false;
    }
    else if(incomingByte == 50) { // read a '2'
      // print the temperature to serial
      Serial.println(readTemp(CS, TEMP_FUDGE_FACTOR, 10)); 
    }
  }
}
 

