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
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 600000 });
config.browserNoActivityTimeout = 600000;
// A browser drawing on the CPU (CI has no GPU) can be too busy to answer Karma's pings
// for a few seconds: wait, so a stuck flow fails on its own timeout, with its screen
config.pingTimeout = 60000;
config.browserDisconnectTimeout = 60000;

// One browser at a time: they share the fixture servers, and flows change their state (a
// star, the saved play queue) that another browser's flows would see mid-run
config.concurrency = 1;

// The fixture server's port (-Ploak.uitest.port), read by the tests (TestServer)
config.client.loakUitestPort = process.env.LOAK_UITEST_PORT;
// Whether to run the large-library flows (-Ploak.webLargeLibrary), read by WebLargeLibraryFlows
config.client.loakLargeLibrary = process.env.LOAK_WEB_LARGE_LIBRARY === "true";

// Muted browsers (test runs are silent), and the flows start playback from test clicks,
// which browsers don't count as user gestures: let them autoplay
config.customLaunchers = Object.assign({}, config.customLaunchers, {
	ChromeHeadlessLoak: {
		base: "ChromeHeadless",
		flags: ["--mute-audio", "--autoplay-policy=no-user-gesture-required"],
	},
	FirefoxHeadlessLoak: {
		base: "FirefoxHeadless",
		prefs: { "media.volume_scale": "0.0", "media.autoplay.default": 0 },
	},
});
config.browsers = config.browsers.map((browser) => browser + "Loak");

// WebKit (Safari's engine) through Playwright's build of it: `npx playwright install webkit`.
// A profile on disk, as Safari has: a throwaway one has no OPFS for the app's databases.
// It has no mute switch: the page mutes every media element, which also lets it autoplay.
config.plugins.push({
	"launcher:WebKitHeadless": ["type", function (baseBrowserDecorator) {
		baseBrowserDecorator(this);
		const fs = require("fs"), os = require("os"), path = require("path");
		const profile = fs.mkdtempSync(path.join(os.tmpdir(), "loak-webkit-"));
		let browser;
		this.name = "WebKitHeadless";
		this._start = async (url) => {
			browser = await require("playwright").webkit.launchPersistentContext(profile);
			const page = browser.pages()[0] || await browser.newPage();
			await browser.addInitScript(() => {
				const muted = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, "muted");
				Object.defineProperty(HTMLMediaElement.prototype, "muted", {
					get: muted.get, set() { muted.set.call(this, true); }, configurable: true,
				});
				const play = HTMLMediaElement.prototype.play;
				HTMLMediaElement.prototype.play = function () {
					muted.set.call(this, true);
					return play.call(this);
				};
			});
			await page.goto(url);
		};
		this.on("kill", (done) => (browser ? browser.close() : Promise.resolve())
			.then(() => fs.rmSync(profile, { recursive: true, force: true }))
			.then(() => done()));
	}],
});
config.browsers.push("WebKitHeadless");

// -Ploak.webBrowsers=chrome,webkit (build.gradle.kts): only those
const only = (process.env.LOAK_WEB_BROWSERS || "").toLowerCase().split(",").filter(Boolean);
if (only.length) {
	config.browsers = config.browsers.filter((b) => only.some((name) => b.toLowerCase().startsWith(name)));
}
