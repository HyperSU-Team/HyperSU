import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import { exec, spawn } from "./index.js";

beforeEach(() => {
  globalThis.window = Object.create(null);
  globalThis.ksu = {};
});

test("spawn options overload sends an empty argument array", () => {
  let received;
  ksu.spawn = (...args) => { received = args; };
  spawn("tool", { cwd: "/data/local/tmp" });
  assert.equal(received[0], "tool");
  assert.deepEqual(JSON.parse(received[1]), []);
  assert.deepEqual(JSON.parse(received[2]), { cwd: "/data/local/tmp" });
});

test("spawn forwards explicit arguments and streams data before exit", () => {
  let callbackName;
  ksu.spawn = (command, args, options, callback) => {
    assert.equal(command, "tool");
    assert.deepEqual(JSON.parse(args), ["a b", "c'd"]);
    assert.deepEqual(JSON.parse(options), { env: { MODE: "test" } });
    callbackName = callback;
  };
  const child = spawn("tool", ["a b", "c'd"], { env: { MODE: "test" } });
  const events = [];
  child.stdout.on("data", data => events.push(data));
  child.on("exit", code => events.push(code));
  window[callbackName].stdout.emit("data", "output");
  window[callbackName].emit("exit", 0);
  assert.deepEqual(events, ["output", 0]);
  assert.equal(Object.hasOwn(window, callbackName), false);
});

test("spawn emits native startup errors after the caller registers listeners", async () => {
  const failure = new Error("native startup failed");
  ksu.spawn = () => { throw failure; };
  let observed;
  spawn("tool").on("error", error => { observed = error; });
  await Promise.resolve();
  assert.equal(observed, failure);
  assert.deepEqual(Object.keys(window), []);
});

test("event names do not collide with Object prototype properties", () => {
  ksu.spawn = () => {};
  const child = spawn("tool");
  const values = [];
  child.on("constructor", value => values.push(value));
  child.stdout.on("__proto__", value => values.push(value));
  child.emit("constructor", "child");
  child.stdout.emit("__proto__", "stdio");
  assert.deepEqual(values, ["child", "stdio"]);
});

test("exec settles with native output and removes its callback", async () => {
  ksu.exec = (command, options, callback) => {
    assert.equal(command, "tool");
    assert.deepEqual(JSON.parse(options), {});
    window[callback](0, "stdout", "stderr");
  };
  assert.deepEqual(await exec("tool"), { errno: 0, stdout: "stdout", stderr: "stderr" });
  assert.deepEqual(Object.keys(window), []);
});

test("exec rejects native startup errors and removes its callback", async () => {
  ksu.exec = () => { throw new Error("native startup failed"); };
  await assert.rejects(exec("tool"), /native startup failed/);
  assert.deepEqual(Object.keys(window), []);
});
