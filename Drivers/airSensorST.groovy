/*
===== Blebox Hubitat Integration Driver

	Copyright 2019, Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER: The author of this integration is not associated with blebox.  This code uses the blebox
open API documentation for development and is intended for integration into the SmartThings.

===== Hiatory =====
08.22.19	1.0.01	Initial release.  Known issue: Does not work with new SmartThings App.
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "1.0.01" }
metadata {
	definition (name: "bleBox airSensor",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.sensor") {
        capability "Air Quality Sensor"
		attribute "PM_1_Measurement", "string"
		attribute "PM_1_Trend", "string"
		attribute "PM_2_5_Measurement", "string"
		attribute "PM_2_5_Trend", "string"
		attribute "pm2_5Quality", "number"
		attribute "PM_10_Measurement", "string"
		attribute "PM_10_Trend", "string"
		attribute "pm10Quality", "number"
		attribute "airQualityLabel", "string"
 		command "kick"
        command "refresh"
		attribute "kickActive", "boolean"
        capability "Health Check"
	}

	tiles(scale: 2) {
	   	multiAttributeTile(name:"airQuality", type:"device.airQuality", width:6, height:4, canChangeIcon: true) {
    		tileAttribute("device.airQuality", key: "PRIMARY_CONTROL") {
	            attributeState("default", label:'${currentValue}%', backgroundColors:[
	                [value: 25, color: "#4c9900"],
	                [value: 50, color: "#b2ff66"],
	                [value: 75, color: "#ffcc99"],
	                [value: 100, color: "#ff8000"],
	                [value: 101, color: "#bc2323"]
                ])
            }
            tileAttribute("device.airQualityLabel", key: "SECONDARY_CONTROL") {
	            attributeState("default", label: '${currentValue}')
           }
        }
		valueTile("Measurement", "default", width: 2, height: 1) {
			state "default", label: 'Value'
		}
		valueTile("Quality", "default", width: 2, height: 1) {
			state "default", label: 'Quality'
		}
		valueTile("Trend", "default", width: 1, height: 1) {
			state "default", label: 'Trend'
		}
		valueTile("PM1", "default", width: 1, height: 1) {
			state "default", label: 'PM 1:'
		}
		valueTile("PM25", "default", width: 1, height: 1) {
			state "default", label: 'PM 2.5:'
		}
		valueTile("PM10", "default", width: 1, height: 1) {
			state "default", label: 'PM 10:'
		}
		valueTile("PM1Value", "device.PM_1_Measurement", width: 2, height: 1, decoration: "flat") {
			state "default", label: '${currentValue}?g/m3'
		}
		valueTile("PM25Value", "device.PM_2_5_Measurement", width: 2, height: 1, decoration: "flat") {
			state "default", label: '${currentValue}?g/m3'
		}
		valueTile("PM10Value", "device.PM_10_Measurement", width: 2, height: 1, decoration: "flat") {
			state "default", label: '${currentValue}?g/m3'
		}
 		valueTile("PM25Quality", "device.pm2_5Quality", width: 2, height: 1, decoration: "flat") {
			state "default", label: '${currentValue}%'
		}
 		valueTile("PM10Quality", "device.pm10Quality", width: 2, height: 1, decoration: "flat") {
			state "default", label: '${currentValue}%'
		}
 		valueTile("PM1Trend", "device.PM_1_Trend", width: 1, height: 1, decoration: "flat") {
			state "default", label: '${currentValue}'
		}
 		valueTile("PM25Trend", "device.PM_2_5_Trend", width: 1, height: 1, decoration: "flat") {
			state "default", label: '${currentValue}'
		}
 		valueTile("PM10Trend", "device.PM_10_Trend", width: 1, height: 1, decoration: "flat") {
			state "default", label: '${currentValue}'
		}
		standardTile("refresh", "default", width: 2, height: 1, decoration: "flat") {
			state "default", label:"Refresh", action:"refresh"
		}
		valueTile("1x1", "default", decoration: "flat", height: 1, width: 1) {
			state "default", label: ''
		}
		valueTile("2x1", "default", decoration: "flat", height: 1, width: 2) {
			state "default", label: ''
		}
		valueTile("4x1", "default", decoration: "flat", height: 1, width: 4) {
			state "default", label: ''
		}
        main(["airQuality"])
        details(["airQuality",
        		 "1x1", "Measurement", "Quality", "Trend",
        		 "PM1", "PM1Value", "2x1", "PM1Trend",
        		 "PM25", "PM25Value", "PM25Quality", "PM25Trend",
        		 "PM10", "PM10Value", "PM10Quality", "PM10Trend",
                 "4x1", "refresh"])
    }
	preferences {
		input ("device_IP", "text", title: "Manual Install Device IP")
		input ("refreshInterval", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"])
		input ("statusLed", "bool", title: "Enable the Status LED")
		input ("debug", "bool", title: "Enable debug logging")
		input ("descriptionText", "bool", title: "Enable description text logging")
	}
}
def installed() {
	logInfo("Installing...")
	sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
	sendEvent(name: "healthStatus", value: "online")
	sendEvent(name: "DeviceWatch-Enroll", value: [protocol: "LAN", scheme:"untracked"].encodeAsJson(), displayed: false)
	runIn(2, updated)
}
def updated() {
	logInfo("Updating...")
	unschedule()
	
	if (!getDataValue("applicationVersion")) {
		if (!device_IP) {
			logWarn("updated:  deviceIP  is not set.")
			return
		}
		updateDataValue("deviceIP", device_IP)
		logInfo("Device IP set to ${getDataValue("deviceIP")}")
	}

	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery30Minutes(refresh)
	}

	setStatusLed()
	updateDataValue("driverVersion", driverVer())

	refresh()
}

//	===== Commands and updating state =====
def on() { kick() }
def off() { kick() }
def kick() {
	logDebug("kick.")
	sendGetCmd("/api/air/kick", "kickParse")
}
def kickParse(response) {
	logDebug("kickResponse.  Measurement has started and will take about 1 minute for results to show.")
	sendEvent(name: "kickActive", value: true)
	runIn(60, postKick)
}
def postKick() {
	logDebug("postKick.  Measuring quality post Kick.")
	sendEvent(name: "kickActive", value: false)
	refresh()
}
def ping() {
	logDebug("ping")
    refresh()
}
def refresh() {
	logDebug("refesh.")
	sendGetCmd("/api/air/state", "commandParse")
}
def commandParse(response) {
	if(response.status != 200 || response.body == null) {
		logWarn("parseInput: Command generated an error return: ${response.status} / ${response.body}")
		return
	}
	def jsonSlurper = new groovy.json.JsonSlurper()
	def cmdResponse = jsonSlurper.parseText(response.body)
	logDebug("commandParse: cmdResp = ${cmdResponse}")
    
	def pm1Data = cmdResponse.air.sensors.find{ it.type == "pm1" }
    def pm1Value = pm1Data.value.toInteger()
    def pm1Trend = getTrendText(pm1Data.trend)
    
	def pm2_5Data = cmdResponse.air.sensors.find{ it.type == "pm2.5" }
    def pm25Value = pm2_5Data.value.toInteger()
    def pm25Trend = getTrendText(pm2_5Data.trend)
    
	def pm10Data = cmdResponse.air.sensors.find{ it.type == "pm10" }
    def pm10Value = pm10Data.value.toInteger()
    def pm10Trend = getTrendText(pm10Data.trend)
//	===== SIMULATION DATA TO CHECK ALGORITHM =====
//	pm1Value = 50
//	pm25Value = 60
//	pm10Value = 179

//	Create Air Quality Index using EU standard for measurement. Reference:
//	"http://www.airqualitynow.eu/about_indices_definition.php", utilizing the 
//	Background Index for 1 hour against the sensor provided data.  Values are
//	0 to 500 with 100 per the grid values on the index.
	def pm25Quality
	switch(pm25Value) {
    	case 0..30: pm25Quality = (50 * pm25Value / 30).toInteger(); break
        case 31..55: pm25Quality = 20 + pm25Value - 30; break
        case 56..110: pm25Quality = 75 + (25 * (pm25Value - 55) / 55).toInteger(); break
        default: pm25Quality = (0.5 + (100 *pm25Value/110) ).toInteger()
    }

	def pm10Quality
	switch(pm10Value) {
    	case 0..30: pm10Quality = (50 * pm10Value / 50).toInteger(); break
        case 31..55: pm10Quality = 50 + (25 * (pm10Value - 50) / 40).toInteger(); break
        case 56..180: pm10Quality = 75 + (25 * (pm10Value - 90) / 90).toInteger(); break
        default: pm10Quality = (0.5 + (100*pm10Value /180)).toInteger()
    }

	def airQuality = Math.max(pm25Quality, pm10Quality)
    def airQualityLabel
    switch(airQuality) {
    	case 0..25: airQualityLabel = "Very Low" ; break
        case 26..50: airQualityLabel = "Low" ; break
        case 51..75: airQualityLevel = "Medium" ; break
        case 75..100: airQualityLabel = "High" ; break
        default: airQualityLabel = "Very High"
    }

	sendEvent(name: "PM_1_Measurement", value: pm1Value, unit: "micro-g/m3")
	sendEvent(name: "PM_1_Trend", value: pm1Trend)
	sendEvent(name: "PM_2_5_Measurement", value: pm25Value, unit: "micro-g/m3")
	sendEvent(name: "PM_2_5_Trend", value: pm25Trend)
    sendEvent(name: "pm2_5Quality", value: pm25Quality)
	sendEvent(name: "PM_10_Measurement", value: pm10Value, unit: "micro-g/m3")
	sendEvent(name: "PM_10_Trend", value: pm10Trend)
    sendEvent(name: "pm10Quality", value: pm10Quality)
//   	sendEvent(name: "airQuality", value: airQuality, unit: "CAQI")
   	sendEvent(name: "airQuality", value: airQuality)
    sendEvent(name: "airQualityLabel", value: airQualityLabel)
	logInfo("commandParse: Air Quality Data, Index and Category Updated")
}
def getTrendText(trend) {
	def trendText
	switch(trend) {
		case 1: trendText = "Even"; break
		case 2: trendText = "Down"; break
		case 3: trendText = "Up"; break
		default: trendText = "no data"
	}
	return trendText
}


//	===== Set Status LED =====
def setStatusLed() {
	logDebug("setLed")
	def enable = 1
	if (statusLed != true) { enable = 0 }
	sendPostCmd("/api/settings/set",
				"""{"settings":{"statusLed":{"enabled":${enable}}}}""",
				"ledStatusParse")
}
def ledStatusParse(response) {
	if(response.status != 200 || response.body == null) {
		logWarn("parseInput: Command generated an error return: ${response.status} / ${response.body}")
		return
	}
	def jsonSlurper = new groovy.json.JsonSlurper()
	def cmdResponse = jsonSlurper.parseText(response.body)
	state.statusLed = cmdResponse.settings.statusLed.enabled
	logDebug("ledStatusParse: ${cmdResponse}")
}


//	===== Communications =====
private sendGetCmd(command, action){
	logDebug("sendGetCmd: ${command} // ${getDataValue("deviceIP")} // ${action}")
	def parameters = [method: "GET",
    				  path: command,
                      headers: [
                          Host: "${getDataValue("deviceIP")}:80"
                      ]]
	sendHubCommand(new physicalgraph.device.HubAction(parameters, null, [callback: action]))
}
private sendPostCmd(command, body, action){
	logDebug("sendPostCmd: ${command} // ${getDataValue("deviceIP")} // ${action}")
	def parameters = [method: "POST",
					  path: command,
					  body: body,
					  headers: [
						  Host: "${getDataValue("deviceIP")}:80"
					  ]]
	sendHubCommand(new physicalgraph.device.HubAction(parameters, null, [callback: action]))
}


//	===== Utility Methods =====
def logInfo(msg) {
	if (descriptionText != false) { log.info "${device.label} ${driverVer()}:   ${msg}" }
}
def logDebug(msg){
	if(debug == true) { log.debug "${device.label} ${driverVer()}:   ${msg}" }
}
def logWarn(msg){ log.warn "${device.label} ${driverVer()}:    ${msg}" }

//	end-of-file