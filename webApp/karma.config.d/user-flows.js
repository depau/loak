// The user flows load the app's static assets the way the page does, from the site
// root: the sqlite worker (and the sqlite wasm it fetches) and the Compose resources.
for (const asset of ["sqlite-worker.js", "sqlite/", "composeResources/"]) {
	config.files.push({
		pattern: "kotlin/" + asset + (asset.endsWith("/") ? "**/*" : ""),
		included: false, served: true, watched: false,
	});
	config.proxies["/" + asset] = "/base/kotlin/" + asset;
}

// A flow runs for up to a few minutes against a real server
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 300000 });
config.browserNoActivityTimeout = 300000;

// One browser at a time: both share the fixture server, and flows change its state (a
// star, the saved play queue) that the other browser's flows would see mid-run
config.concurrency = 1;

// The fixture server's port (-Ploak.uitest.port), read by the tests (TestServer)
config.client.loakUitestPort = process.env.LOAK_UITEST_PORT;

// The flows start playback from test clicks, which browsers don't count as user
// gestures: let them autoplay
config.customLaunchers = Object.assign({}, config.customLaunchers, {
	ChromeHeadlessAutoplay: { base: "ChromeHeadless", flags: ["--autoplay-policy=no-user-gesture-required"] },
	FirefoxHeadlessAutoplay: { base: "FirefoxHeadless", prefs: { "media.autoplay.default": 0 } },
});
config.browsers = config.browsers.map((browser) => browser + "Autoplay");
