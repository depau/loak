// Reports a frozen page to Sentry (started by SentryWeb.kt). A blocked main
// thread can't report itself and @sentry/browser has no hang detection, so the
// page sends this worker a heartbeat every second plus each breadcrumb; when the
// heartbeats stop, the worker posts an "App Hanging" event to Sentry itself.
'use strict';

var HANG_MS = 5000;
var MAX_CRUMBS = 30;

var config = null;
var lastBeat = 0;
var visible = true;
var reported = false;
var crumbs = [];
var lastTick = Date.now();

self.onmessage = function (event) {
	var msg = event.data;
	if (msg.config) {
		config = msg.config;
	} else if ('beat' in msg) {
		lastBeat = Date.now();
		visible = msg.beat;
		reported = false;
	} else {
		crumbs.push(msg);
		if (crumbs.length > MAX_CRUMBS) crumbs.shift();
	}
};

setInterval(function () {
	var now = Date.now();
	// the worker itself was suspended (machine asleep, tab frozen): not a hang
	if (now - lastTick > 3000) lastBeat = now;
	lastTick = now;
	// hidden tabs throttle the page's heartbeat timer to once a minute. A page
	// that freezes while hidden goes unreported.
	if (!config || !lastBeat || !visible || reported) return;
	var blocked = now - lastBeat;
	if (blocked < HANG_MS) return;
	reported = true;
	report(blocked);
}, 1000);

function lastScreen() {
	for (var i = crumbs.length - 1; i >= 0; i--) {
		var c = crumbs[i];
		if (c.category === 'navigation' && c.data && c.data.to) return String(c.data.to);
	}
	return 'unknown';
}

function report(blockedMs) {
	var dsn = new URL(config.dsn);
	var url = dsn.protocol + '//' + dsn.host + '/api' + dsn.pathname +
		'/envelope/?sentry_version=7&sentry_key=' + dsn.username;
	var id = crypto.randomUUID().replace(/-/g, '');
	var screen = lastScreen();
	var event = {
		event_id: id,
		timestamp: Date.now() / 1000,
		platform: 'javascript',
		level: 'error',
		release: config.release,
		dist: config.dist,
		environment: config.environment,
		exception: {
			values: [{
				type: 'App Hanging',
				value: 'Main thread unresponsive for ' + Math.round(blockedMs / 1000) + ' s',
				mechanism: { type: 'loak.hang_watchdog', handled: false }
			}]
		},
		// one issue per screen the hang happened on
		tags: { 'hang.screen': screen },
		fingerprint: ['web-app-hang', screen],
		breadcrumbs: crumbs,
		// no PII: never let Sentry derive the sender's IP address
		sdk: { name: 'loak.web.hang-watchdog', version: '1', settings: { infer_ip: 'never' } }
	};
	var body = JSON.stringify({ event_id: id, sent_at: new Date().toISOString() }) + '\n' +
		JSON.stringify({ type: 'event' }) + '\n' + JSON.stringify(event);
	fetch(url, { method: 'POST', body: body }).catch(function () {});
}
