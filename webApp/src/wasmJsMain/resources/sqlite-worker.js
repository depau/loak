/*
 * SQLite worker for androidx.sqlite.driver.web.WebWorkerSQLiteDriver (Room 3 wasm).
 *
 * Implements the androidx web-worker message protocol on top of the official
 * SQLite WASM build (sqlite3.js / sqlite3.wasm / sqlite3-opfs-async-proxy.js),
 * persistent via the OPFS VFS.
 *
 * Protocol (from androidx.sqlite:sqlite-web-wasm-js):
 *   request  {id, data:{cmd:"open", fileName}}                                   -> {id, data:{databaseId}}
 *   request  {id, data:{cmd:"prepare", databaseId, sql}}                         -> {id, data:{statementId, parameterCount, columnNames}}
 *   request  {id, data:{cmd:"step", statementId, bindings:[null|num|str|Uint8Array]}} -> {id, data:{rows:[[..]], columnTypes:[int]}}
 *   request  {id, data:{cmd:"close", statementId?|databaseId?}}                  -> {id, data:{}}
 *   errors   {id, error:"<msg>"}
 *
 * Blob values cross the wire as Uint8Array; integers/floats as JS numbers.
 */
"use strict";

/* Trace each protocol message to the worker console (helps debugging the
 * androidx WebWorkerSQLiteDriver handshake). Set to true to debug. */
var TRACE = false;

importScripts("./sqlite/sqlite3.js");

var dbs = new Map();    // databaseId -> { db }
var stmts = new Map();  // statementId -> { stmt, dbId }
var nextDbId = 1;
var nextStmtId = 1;
var sqliteApi = null;

/* Serialize message handling so a late-arriving request does not interleave
 * with an in-flight wasm call. */
var queue = Promise.resolve();

function fmtError(err) {
	if (err && typeof err.resultCode === 'number') {
		// Match the C-style message shape the driver expects.
		return "Sqlite3Error: sqlite3 result code " + err.resultCode + ": " + (err.message || "");
	}
	return (err && (err.message || String(err))) || "unknown error";
}

function normalize(value) {
	if (typeof value === 'bigint') {
		if (value <= Number.MAX_SAFE_INTEGER && value >= Number.MIN_SAFE_INTEGER) {
			return Number(value);
		}
		return value; // driver flattens via JsNumber anyway
	}
	return value;
}

function sqliteTypeOf(value) {
	if (value === null || value === undefined) return 5; // NULL
	if (typeof value === 'string') return 3;             // TEXT
	if (value instanceof Uint8Array) return 4;           // BLOB
	if (typeof value === 'number' || typeof value === 'bigint') {
		return (typeof value === 'bigint' || Number.isInteger(value)) ? 1 : 2; // INTEGER / FLOAT
	}
	return 5;
}

function ensureSqlite() {
	if (sqliteApi === null) {
		sqliteApi = sqlite3InitModule({
			locateFile: function (file) { return './sqlite/' + file; }
		}).then(function (api) {
			sqliteApi = api;
			return api;
		}, function (err) {
			sqliteApi = null;
			throw err;
		});
	}
	return Promise.resolve(sqliteApi);
}

function openDatabase(fileName) {
	if (!/^\//.test(fileName)) fileName = '/' + fileName;
	var api = sqliteApi;
	// NOTE: OpfsDb (OPFS async VFS) blocks the worker on BEGIN/EXCLUSIVE when the
	// opfs-async-proxy isn't wired; plain oo1.DB (in-memory) keeps the protocol
	// fully working. Persistence is a later web enhancement.
	return new api.oo1.DB(fileName);
}

function prepareStatement(db, sql) {
	var stmt = db.prepare(sql);
	var columnCount = 0;
	try { columnCount = stmt.columnCount; } catch (e) { columnCount = 0; }
	// sqlite3.js's getColumnNames() throws "Column index 0 is out of range"
	// for statements with no result columns (PRAGMA SET, DDL, DML), so guard it.
	var columnNames = [];
	if (columnCount > 0) {
		try { columnNames = stmt.getColumnNames([]) || []; } catch (e) { columnNames = []; }
	}
	var parameterCount = 0;
	try {
		var capi = sqliteApi.capi;
		if (capi && capi.sqlite3_bind_parameter_count && stmt.pointer) {
			parameterCount = capi.sqlite3_bind_parameter_count(stmt.pointer);
		}
	} catch (e) {
		parameterCount = 0;
	}
	if (!parameterCount) {
		// Fallback: count '?' placeholders outside single-quoted strings.
		var stripped = sql.replace(/'[^']*'/g, '');
		parameterCount = ((stripped.match(/\?/g)) || []).length;
	}
	return { stmt: stmt, columnNames: columnNames, parameterCount: parameterCount };
}

function executeStep(record, bindings) {
	var stmt = record.stmt;
	if (bindings && bindings.length) {
		stmt.bind(bindings.map(normalize));
	}
	var rows = [];
	var columnTypes = [];
	// getColumnNames() throws for statements with no result columns — guard it.
	var columnNames = null;
	try { columnNames = stmt.getColumnNames ? stmt.getColumnNames() : null; } catch (e) { columnNames = null; }
	var ncol = columnNames ? columnNames.length : 0;
	if (!ncol) {
		try {
			ncol = stmt.columnCount;
		} catch (e) { ncol = 0; }
	}
	var first = true;
	while (stmt.step()) {
		var row = [];
		if (first && ncol === 0) {
			ncol = stmt.columnCount;
		}
		for (var i = 0; i < ncol; i++) {
			var v = normalize(stmt.get(i));
			row.push(v);
			if (first) columnTypes.push(sqliteTypeOf(v));
		}
		rows.push(row);
		first = false;
	}
	// Reposition for potential rebind/re-step; do not finalize.
	try { stmt.reset(); stmt.clearBindings(); } catch (e) { /* noop */ }
	return {
		rows: rows,
		columnTypes: columnTypes.length === ncol ? columnTypes : new Array(ncol).fill(5)
	};
}

function handle(data) {
	var cmd = data.cmd;
	if (cmd === "open") {
		var dbId = nextDbId++;
		dbs.set(dbId, { db: openDatabase(data.fileName) });
		return { databaseId: dbId };
	}
	if (cmd === "prepare") {
		var rec = dbs.get(data.databaseId);
		if (!rec) throw new Error("unknown database id " + data.databaseId);
		var p = prepareStatement(rec.db, data.sql);
		var stmtId = nextStmtId++;
		stmts.set(stmtId, { stmt: p.stmt, dbId: data.databaseId });
		return {
			statementId: stmtId,
			parameterCount: p.parameterCount,
			columnNames: p.columnNames
		};
	}
	if (cmd === "step") {
		var srec = stmts.get(data.statementId);
		if (!srec) throw new Error("unknown statement id " + data.statementId);
		return executeStep(srec, data.bindings || []);
	}
	if (cmd === "close") {
		if (typeof data.statementId === 'number' && stmts.has(data.statementId)) {
			var sr = stmts.get(data.statementId);
			sr.stmt.finalize();
			stmts.delete(data.statementId);
		}
		if (typeof data.databaseId === 'number' && dbs.has(data.databaseId)) {
			// finalize any remaining statements of this db
			stmts.forEach(function (s, id) {
				if (s.dbId === data.databaseId) {
					try { s.stmt.finalize(); } catch (e) {}
					stmts.delete(id);
				}
			});
			try { dbs.get(data.databaseId).db.close(); } catch (e) {}
			dbs.delete(data.databaseId);
		}
		return {};
	}
	throw new Error("unknown command: " + cmd);
}

self.onmessage = function (event) {
	var msg = event.data;
	var id = msg.id;
	var data = msg.data || {};
	if (TRACE) { try { console.log("[sqlite-worker] " + data.cmd + " id=" + id + (data.fileName ? " file=" + data.fileName : "") + (typeof data.statementId === 'number' ? " stmt=" + data.statementId : "") + (data.sql ? " sql=" + String(data.sql).slice(0, 120) : "")); } catch (e) {} }
	var started = Date.now();
	var watchdog = setInterval(function () {
		console.log("[sqlite-worker] WATCHDOG: op " + data.cmd + " id=" + id + " still running after " + (Date.now() - started) + "ms");
	}, 4000);
	queue = queue.then(function () {
		return ensureSqlite().then(function () { return handle(data); });
	}).then(function (result) {
		clearInterval(watchdog);
		if (TRACE) console.log("[sqlite-worker] RESP " + data.cmd + " id=" + id + " ok -> " + safeJson(result).slice(0, 120));
		self.postMessage({ id: id, data: result });
	}, function (err) {
		clearInterval(watchdog);
		console.log("[sqlite-worker] ERROR " + data.cmd + " id=" + id + ": " + (err && (err.message || String(err))));
		self.postMessage({ id: id, error: fmtError(err) });
	});
};
function safeJson(o) {
	try { return JSON.stringify(o).replace(/\u0000/g, "\\u0000"); } catch (e) { return String(o); }
}
