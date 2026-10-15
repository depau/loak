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

// The fixture server's port (-Ploak.uitest.port), read by the tests (TestServer)
config.client.loakUitestPort = process.env.LOAK_UITEST_PORT;
