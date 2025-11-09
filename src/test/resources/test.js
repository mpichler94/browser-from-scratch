console.log("Hello from test.js");

const h1 = document.querySelectorAll("h1")[0]
const id = h1.getAttribute("id")
console.log(id)

function lengthCheck() {
    const name = this.getAttribute("name")
    const value = this.getAttribute("value")
    if (value.length > 100) {
        console.log("Input" + name + " has too much text")
    }
    console.log("Input" + name + " has " + value.length + " characters")
}
const inputs = document.querySelectorAll("input")
for (var i = 0; i < inputs.length; i++) {
    inputs[i].addEventListener("keydown", lengthCheck)
}

const button = document.querySelectorAll("button")
button[0].addEventListener("click", function(evt) {
    console.log("Button clicked")
    evt.stopPropagation()
})

const form = document.querySelectorAll("form")[0]
console.log(form.children()[0].getAttribute("name"))

let btn = document.createElement("button")
btn.innerHTML = "Click me"
form.appendChild(btn)

let foo = document.createElement("span")
foo.innerHTML = "Hello"
form.insertBefore(foo, btn)

let b2 = form.removeChild(btn)
form.insertBefore(b2, foo)

console.log(heading)

form.addEventListener("click", function() {console.log("Button in form clicked") })

console.log(form.outerHTML)

console.log(b2.innerHTML)

console.log(form.innerHTML)

document.cookie = "foo=bar; SameSite"

console.log("Cookie:" + document.cookie)

const x = new XMLHttpRequest()
x.open("GET", "https://webbrowsertools.com/test-cors/", false)
x.send()