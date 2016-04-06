'use strict';

var MAXIMUM_LOGS = 150;

var DEFAULT_COLOR = '#66AAFF';
var ACTIVE_COLOR = '#DDDD22';
var SUCCESS_COLOR = '#22DD22';
var ERROR_COLOR = '#FF6644';

var HIGHLIGHT_TIME = 400;

var logs = [];
var filteredLogs = [];
var filter = {
	"selectedLevels" : [],
	"selectedLoggers" : ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"],
	"searchText" : ""
};
var loggers = [];

var nodes, edges, network;

function highlightNode(id, highlightColor, time) {
	console.log("Update node with id: " + id);
	nodes.update({id: id, color: highlightColor});

		function resetHightlight() {
			nodes.update({id: id, color: DEFAULT_COLOR});
		}

		setTimeout(resetHightlight, time);
}

function updateTable() {
	document.getElementById("log-table-body").innerHTML = "";

	var i = 0;
	for (i = 0; i < filteredLogs.length; i++) {
		document.getElementById("log-table-body").innerHTML += "<tr class=\""+filteredLogs[i].level+"\"><td>"
			+(new Date(filteredLogs[i].timeMillis).toLocaleTimeString())
			+"</td><td>"+filteredLogs[i].level
			+"</td><td>"+filteredLogs[i].loggerName
			+"</td><td>"+filteredLogs[i].message+"</td></tr>";
	}
}

function updateLoggers() {
	document.getElementById("loggers").innerHTML = "";

	var i = 0;
	for (i = 0; i < loggers.length; i++) {
		var loggerOk = false;
		var j = 0;
		for (j = 0; j < filter.selectedLoggers.length; j++) {
			if(loggers[i] == filter.selectedLoggers[j]) {
				loggerOk = true;
			}
		}

		if (loggerOk) {
			document.getElementById("loggers").innerHTML += "<label><input id=\""+loggers[i]+"\" type=\"checkbox\" checked>"+loggers[i]+"</label>";
		} else {
			document.getElementById("loggers").innerHTML += "<label><input id=\""+loggers[i]+"\" type=\"checkbox\">"+loggers[i]+"</label>";
		}
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

	console.log(filter);
}

function initNetwork() {
	nodes = new vis.DataSet();
	edges = new vis.DataSet();

	var container = document.getElementById("network-graph");

	nodes.add({id: "broker", label: "Broker", color: "#FFCC44", shape: "box", font:{size:20}});

	var data = {
		nodes: nodes,
		edges: edges
	};
	var options = {
		autoResize: true,
		height: '100%',
		width: '100%',
		interaction:{
			dragNodes:false,
			dragView: true,
			hover: false,
		},
		physics: {
			enabled: true
		}
	}
	network = new vis.Network(container, data, options);
}

function updateNetwork(log) {
	var message = JSON.parse(log.message);

	if(message.event == "client_started") {
		nodes.add({id: message.data, color: DEFAULT_COLOR, label: "IoT Endpoint "+message.data});
		edges.add({id: message.data, from: message.data, to: "broker"});
	}
	if(message.event == "client_stopped") {
		nodes.remove({id: message.data});
		edges.remove({id: message.data});
	}

	if(message.event == "protect_message") {
		highlightNode(message.data, ACTIVE_COLOR, HIGHLIGHT_TIME);
	}

	if(message.event == "protect_message_success") {
		highlightNode(message.data, SUCCESS_COLOR, HIGHLIGHT_TIME);
	}

	if(message.event == "protect_message_error") {
		highlightNode(message.data, ERROR_COLOR, HIGHLIGHT_TIME);
	}

	if(message.event == "retrieve_message") {
		highlightNode(message.data, ACTIVE_COLOR, HIGHLIGHT_TIME);
	}

	if(message.event == "retrieve_message_success") {
		highlightNode(message.data, SUCCESS_COLOR, HIGHLIGHT_TIME);
	}

	if(message.event == "retrieve_message_error") {
		highlightNode(message.data, ERROR_COLOR, HIGHLIGHT_TIME);
	}

	if(message.event == "server_started") {
		nodes.add({id: message.data, label: "Tenant "+message.data, color: "#7BE141"});
	}
	if(message.event == "server_stopped") {
		nodes.remove({id: message.data});
	}

	network.fit();
}

function checkForNewLogger(log) {
	var existing = false;
	var i = 0;
	for (i = 0; i < loggers.length; i++) {
		if(loggers[i] == log.loggerName) {
			existing = true;
		}
	}

	if (!existing) {
		filter.selectedLoggers.push(log.loggerName);
		loggers.push(log.loggerName);
		updateLoggers();
	}
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
	for (i = 0; i < filter.selectedLoggers.length; i++) {
		if(filter.selectedLoggers[i] == log.loggerName) {
			loggerOk = true;
		}
	}

	var searchOk = filter.searchText == undefined || filter.searchText == "" || log.message.indexOf(filter.searchText) > -1;

	return searchOk && logLevelOk && loggerOk;
}

function onNewLog(log) {
	if(log.level == "EVENT") {
		// Update network
		updateNetwork(log);

	} else {
		// Append log
		checkForNewLogger(log);
		if (logIsOk(log)) {
			//document.getElementById("log-table-body").innerHTML.replace(>", "");
			/*
		document.getElementById("log-table-body").innerHTML += "<tr class=\""+log.level+"\"><td>"
			+log.timeMillis
			+"</td><td>"+log.level
			+"</td><td>"+log.loggerName
			+"</td><td>"+log.message+"</td></tr>";
			*/
		}
		logs.push(log);

		if(logs.length > MAXIMUM_LOGS) {
			logs.shift();
		}

		applyFilter();
	}
}

function applyFilter() {
	updateFilter();
	filteredLogs = [];

	var i = 0;
	for (i = 0; i < logs.length; i++) {
		if(logIsOk(logs[i])) {
			filteredLogs.push(logs[i]);
		}
	}
	updateTable();
}


function startWebsocketConnection() {
	if ("WebSocket" in window)
	{
		var port = window.location.port
		//var ws = new WebSocket("ws://"+window.location.hostname+(window.location.port ? ":"+location.port : "")+"/logs");
		var ws = new WebSocket("ws://localhost:3000/logs");

		ws.onopen = function()
		{
			console.log("WebSocket connection opened.");
		};

		ws.onmessage = function (evt)
		{
			onNewLog(JSON.parse(evt.data));
		};

		ws.onclose = function()
		{
			console.log("WebSocket connection closed.");
		};
	}
	else
	{
		alert("WebSockets are not supported by your Browser!");
	}
}

function showLogging() {
	document.getElementById("filters").className = "";
	document.getElementById("log-table").className = "";
	document.getElementById("network-button").className = "";
	document.getElementById("logging-button").className = "hide";
	document.getElementById("network").className = "hide";
}

function showNetwork() {
	document.getElementById("filters").className = "hide";
	document.getElementById("log-table").className = "hide";
	document.getElementById("network-button").className = "hide";
	document.getElementById("logging-button").className = "";
	document.getElementById("network").className = "";
	network.fit();
}

// This function is called after the page is loaded
function start() {
	startWebsocketConnection();
	updateTable();
	initNetwork();
}
