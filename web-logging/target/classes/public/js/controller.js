'use strict';

var logs = [];
var filteredLogs = [{
	"timeMillis" : 1455144912438,
	"thread" : "main",
	"level" : "INFO",
	"loggerName" : "de.ericdoerheit.snippets.log4j.LoggingTest",
	"message" : "Test",
	"endOfBatch" : false,
	"loggerFqcn" : "org.apache.logging.slf4j.Log4jLogger"
}];
var filter = {
	"selectedLevels" : [],
	"selectedLoggers" : [],
	"searchText" : ""
};
var loggers = ["de.ericdoerheit.snippets.log4j.LoggingTest"];

function updateTable() {
	document.getElementById("log-table-body").innerHTML = "";

	var i = 0;
	for (i = 0; i < filteredLogs.length; i++) {
		document.getElementById("log-table-body").innerHTML += "<tr><td>"
			+filteredLogs[i].timeMillis
			+"</td><td>"+filteredLogs[i].level
			+"</td><td>"+filteredLogs[i].loggerName
			+"</td><td>"+filteredLogs[i].message+"</td></tr>";
	}
}

function updateLoggers() {
	document.getElementById("loggers").innerHTML = "";

	var i = 0;
	for (i = 0; i < loggers.length; i++) {
		document.getElementById("loggers").innerHTML += "<label><input id=\""+loggers[i]+"\" type=\"checkbox\">"+loggers[i]+"</label>";
	}
}

function updateFilter() {
	filter = {
		"selectedLevels" : [],
		"selectedLoggers" : [],
		"searchText" : ""
	};

	var i = 0;
	for (i = 0; i < loggers.length; i++) {
		if (document.getElementById(loggers[i]).checked) {
			filter.selectedLoggers.push(loggers[i]);
		}
	}

	if (document.getElementById("trace-level").checked) {
		filter.selectedLevels.push("TRACE");
	}

	if (document.getElementById("debug-level").checked) {
		filter.selectedLevels.push("DEBUG");
	}

	if (document.getElementById("info-level").checked) {
		filter.selectedLevels.push("INFO");
	}

	if (document.getElementById("warning-level").checked) {
		filter.selectedLevels.push("WARN");
	}

	if (document.getElementById("error-level").checked) {
		filter.selectedLevels.push("ERROR");
	}

	filter.searchText = document.getElementById("search-text").value;
}

function checkForNewLogger(log) {
	var logger = log.loggerName;

	var existing = false;
	var i = 0;
	for (i = 0; i < loggers.length; i++) {
		if(loggers[i] == log.loggerName) {
			existing = true;
		}
	}

	if (!existing) {
		loggers.push(log.loggerName);
	}
}


function onNewLog(log) {
	checkForNewLogger(log);
	if (logIsOk(log)) {
		document.getElementById("log-table-body").innerHTML.replace("</tr>", "");
		document.getElementById("log-table-body").innerHTML += "<tr><td>"
			+log.timeMillis
			+"</td><td>"+log.level
			+"</td><td>"+log.loggerName
			+"</td><td>"+log.message+"</td></tr>";
	}
	logs.push(log);
}

function logIsOk(log) {	
	var logLevelOk = false;
	var i = 0;
	for (i = 0; i < filter.selectedLevels.length; i++) {
		if(filter.selectedLevels[i] == log.level) {
			logLevelOk = true;
		}
	}

	var loggerOk = false;
	i = 0;
	for (i = 0; i < filter.selectedLevels.length; i++) {
		if(filter.selectedLoggers[i] == log.loggerName) {
			loggerOk = true;
		}
	}

	return log.message.indexOf(substring) > -1 && logLevelOk && loggerOk;
}

function applyFilter() {
	filteredLogs = [];

	var i = 0;
	for (i = 0; i < logs.length; i++) {
		if(logIsOk(logs[i])) {
			filteredLogs.push(logs[i]);
		}
	}
}


function startWebsocketConnection() {
	if ("WebSocket" in window)
	{  
		var port = window.location.port
		var ws = new WebSocket("ws://"+window.location.hostname+(window.location.port ? ":"+location.port : "")+"/logs");

		ws.onopen = function()
		{
			// Web Socket is connected, send data using send()
			ws.send("Message to send");
			alert("Message is sent...");
		};

		ws.onmessage = function (evt) 
		{ 
			var received_msg = evt.data;
			alert("Message is received...");
		};

		ws.onclose = function()
		{ 
			// websocket is closed.
			alert("Connection is closed..."); 
		};
	}
	else
	{
		alert("WebSockets are not supported by your Browser!");
	}
}

// This function is called after the page is loaded
function start() {
	updateTable();
	updateLoggers();

	checkForNewLogger({
		"loggerName" : "bla",
	});

	checkForNewLogger({
		"loggerName" : "bla",
	});
	updateLoggers();
}