#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <OneWire.h>
#include <DallasTemperature.h>

const String DEVICE_WIFI_NAME = "heater v1.0";

const int SERIAL_SPEED = 115200;

const int SENSOR_DATA_PIN = 14;
const int HEATING_PIN_1 = 16;
const int HEATING_PIN_2 = 4;
const int COOLING_PIN_1 = 5;
const int COOLING_PIN_2 = 0;
const int POWER_LED_PIN = 12;
const int TEMPERATURE_CONTROL_LED_PIN = 13;

const long int MAX_TEMP = 50;
const long int MIN_TEMP = 10;

const int PRINT_DELAY = 2000;

IPAddress local_IP(192,168,4,22);
IPAddress gateway(192,168,4,9);
IPAddress subnet(255,255,255,0);

ESP8266WebServer server(80);

OneWire oneWire(SENSOR_DATA_PIN);
DallasTemperature sensors(&oneWire);

struct DeviceSettings{
  int minTemperature = 20, maxTemperature = 30, currentTemperature = 0;
  bool isEnabled = false; // работает ли контроль температуры
  bool isHeatingElementEnabled = false; // работает ли нагревательный элемент
  bool isCoolingElementEnabled = false; // работает ли охлаждение элемент
};

DeviceSettings deviceSettings;

void onHeating(){
  digitalWrite(HEATING_PIN_1, HIGH);
  digitalWrite(HEATING_PIN_2, HIGH);
  deviceSettings.isHeatingElementEnabled = true;
  offCooling();
}

void offHeating(){
  digitalWrite(HEATING_PIN_1, LOW);
  digitalWrite(HEATING_PIN_2, LOW);
  deviceSettings.isHeatingElementEnabled = false;
}

void onCooling(){
  digitalWrite(COOLING_PIN_1, HIGH);
  digitalWrite(COOLING_PIN_2, HIGH);
  deviceSettings.isCoolingElementEnabled = true;
  offHeating();
}

void offCooling(){
  digitalWrite(COOLING_PIN_1, LOW);
  digitalWrite(COOLING_PIN_2, LOW);
  deviceSettings.isCoolingElementEnabled = false;
}

long int timer = 0;

void setup(){
  Serial.begin(SERIAL_SPEED);
  Serial.println();

  Serial.print("Setting soft-AP configuration ... ");
  Serial.println(WiFi.softAPConfig(local_IP, gateway, subnet) ? "Ready" : "Failed!");

  Serial.print("Setting soft-AP ... ");
  Serial.println(WiFi.softAP(DEVICE_WIFI_NAME) ? "Ready" : "Failed!");

  Serial.print("Soft-AP IP address = ");
  Serial.println(WiFi.softAPIP());
  
  server.on("/", [](){
    if (server.arg("maxTemperature") != ""){
      deviceSettings.maxTemperature = min(MAX_TEMP, server.arg("maxTemperature").toInt());
      deviceSettings.minTemperature = min(deviceSettings.minTemperature, deviceSettings.maxTemperature);
    }
    if (server.arg("minTemperature") != ""){
      deviceSettings.minTemperature = max(server.arg("minTemperature").toInt(), MIN_TEMP);
      deviceSettings.maxTemperature = max(deviceSettings.minTemperature, deviceSettings.maxTemperature);
    }
    if (server.arg("isEnabled") != ""){
      deviceSettings.isEnabled = server.arg("isEnabled") == "true";
    }
    
    server.send(200, "application/json", 
      "{\"currentTemperature\":" + String(deviceSettings.currentTemperature) +
      ", \"maxTemperature\":" + String(deviceSettings.maxTemperature) +
      ", \"minTemperature\":" + String(deviceSettings.minTemperature) +
      ", \"isEnabled\":" + (deviceSettings.isEnabled? "true": "false") +
      ", \"isCoolingElementEnabled\":" + (deviceSettings.isCoolingElementEnabled? "true": "false") +
      ", \"isHeatingElementEnabled\":" + (deviceSettings.isHeatingElementEnabled? "true": "false") + "}"
    );
  });
  
  server.begin();
  Serial.println("HTTP server started");

  sensors.begin();
  
  pinMode(COOLING_PIN_1, OUTPUT);
  pinMode(COOLING_PIN_2, OUTPUT);
  pinMode(HEATING_PIN_1, OUTPUT);
  pinMode(HEATING_PIN_2, OUTPUT);
  pinMode(POWER_LED_PIN, OUTPUT);
  pinMode(TEMPERATURE_CONTROL_LED_PIN, OUTPUT);
  
  digitalWrite(POWER_LED_PIN, HIGH);
  digitalWrite(TEMPERATURE_CONTROL_LED_PIN, LOW);
  digitalWrite(COOLING_PIN_1, LOW);
  digitalWrite(HEATING_PIN_1, LOW);
  digitalWrite(COOLING_PIN_2, LOW);
  digitalWrite(HEATING_PIN_2, LOW);
}

void loop() {
  server.handleClient();
  sensors.requestTemperatures();
  deviceSettings.currentTemperature = sensors.getTempCByIndex(0);
  if (deviceSettings.isEnabled){
    digitalWrite(TEMPERATURE_CONTROL_LED_PIN, HIGH);
    if (deviceSettings.currentTemperature < deviceSettings.minTemperature){
      onHeating();
    }else{
      if (deviceSettings.currentTemperature > deviceSettings.maxTemperature){
        onCooling();
      }else{
        offCooling();
        offHeating();
      }
    }
  }else{
    digitalWrite(TEMPERATURE_CONTROL_LED_PIN, LOW);
    offHeating();
    offCooling();
  }

  if (millis() - timer > PRINT_DELAY){
    timer = millis();
    Serial.println("Current temperature " + String(deviceSettings.currentTemperature));
    Serial.println("Maximum temperature " + String(deviceSettings.maxTemperature));
    Serial.println("Minimum temperature " + String(deviceSettings.minTemperature));
    Serial.print("Temperature control ");
    Serial.println(deviceSettings.isEnabled? "true": "false");
    Serial.print("Cooling element ");
    Serial.println(deviceSettings.isCoolingElementEnabled? "true": "false");
    Serial.print("Heating element ");
    Serial.println(deviceSettings.isHeatingElementEnabled? "true": "false");
  }
}
