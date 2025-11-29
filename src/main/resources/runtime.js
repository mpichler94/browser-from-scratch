document = {
  querySelectorAll(s) {
    const handles = __document.querySelectorAll(s)
    return handles.map((h) => new Node(h))
  },

  createElement(tagName) {
    const handle = __document.createElement(tagName)
    return new Node(handle)
  },

  get cookie() {
    return __document.getCookie()
  },

  set cookie(value) {
    __document.setCookie(value)
  }
}

window = {}
LISTENERS = {}
SET_TIMEOUT_REQUESTS = {}
XHR_REQUESTS = {}
RAF_LISTENERS = []

function setTimeout(callback, time_delta) {
  const handle = Object.keys(SET_TIMEOUT_REQUESTS).length
  SET_TIMEOUT_REQUESTS[handle] = callback
  __document.setTimeout(handle, time_delta)
}

function requestAnimationFrame(fn) {
  RAF_LISTENERS.push(fn)
  __document.requestAnimationFrame()
}

function __runSetTimeout(handle) {
  const callback = SET_TIMEOUT_REQUESTS[handle]
  callback()
}

function __runXHROnload(body, handle) {
  const obj = XHR_REQUESTS[handle]
  const evt = new Event("load")
  obj.responseText = body
  if (obj.onload)
    obj.onload(evt)
  return evt.do_default
}

function __runRAFHandlers() {
  const handlers_copy = RAF_LISTENERS
  RAF_LISTENERS = []
  for (const handler of handlers_copy) {
    handler()
  }
}

class Node {
  constructor(handle) {
    this.handle = handle
  }

  getAttribute(attr) {
    return __document.getAttribute(this.handle, attr)
  }

  children() {
    return __document.getChildren(this.handle).map((h)=> new Node(h))
  }

  insertBefore(n, ref) {
    __document.insertBefore(this.handle, n.handle, ref.handle)
  }

  appendChild(n) {
    __document.insertBefore(this.handle, n.handle, null)
  }

  removeChild(n) {
    const handle = __document.removeChild(this.handle, n.handle)
    return new Node(handle)
  }

  addEventListener(type, listener) {
    if (!LISTENERS[this.handle]) LISTENERS[this.handle] = {}
    const dict = LISTENERS[this.handle]
    if (!dict[type]) dict[type] = []
    const list = dict[type]
    list.push(listener)
  }

  dispatchEvent(evt) {
    const type = evt.type
    const handle = this.handle
    const list = (LISTENERS[handle]?.[type]) || []
    for (const element of list) {
      element.call(this, evt)
    }
    return { do_default: evt.do_default, propagate: evt.propagate }
  }

  set innerHTML(s) {
    __document.setInnerHTML(this.handle, s.toString())
  }

  get innerHTML() {
    return __document.getInnerHTML(this.handle)
  }

  get outerHTML() {
    return __document.getOuterHTML(this.handle)
  }
}

class Event {
  constructor(type) {
    this.type = type
    this.do_default = true
    this.propagate = true
  }

  preventDefault() {
    this.do_default = false
  }

  stopPropagation() {
    this.propagate = false
  }
}

const ids = __document.getIDs()
for (let id in ids) {
  globalThis[id] = new Node(ids[id])
  window[id] = this[id]
}

class XMLHttpRequest {
  constructor() {
    this.handle = Object.keys(XHR_REQUESTS).length
    XHR_REQUESTS[this.handle] = this
  }

  open(method, url, is_async) {
    this.is_async = is_async
    this.method = method
    this.url = url
  }

  send(body) {
    this.responseText = __document.sendXMLHttpRequest(this.method, this.url, body, this.is_async, this.handle)
  }
}

console.log("Runtime.js loaded")
