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

  }

  open(method, url, is_async) {
    if (is_async) throw Error("Asynchronous XHR is not supported")
    this.method = method
    this.url = url
  }

  send(body) {
    this.responseText = __document.sendXMLHttpRequest(this.method, this.url, body)
  }
}

console.log("Runtime.js loaded")