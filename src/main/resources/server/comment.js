var strong = document.querySelectorAll("strong")[0];

var allowSubmit = true;

function lengthCheck() {
    var value = this.getAttribute("value");
    allowSubmit = value.length <= 100;
    if (!allowSubmit) {
        strong.innerHTML = "Comment too long!";
    }
}

var inputs = document.querySelectorAll("input");
for (var i = 0; i < inputs.length; i++) {
    inputs[i].addEventListener("keydown", lengthCheck);
}

var form = document.querySelectorAll("form")[0];
form.addEventListener("submit", function(e) {
    console.log("Handle submit");
    if (!allowSubmit) e.preventDefault();
});

console.log("Cookie:" + document.cookie)

const x = new XMLHttpRequest()
x.open("GET", "/example4-form.html", true)
x.send()
x.onload = function(evt) { console.log('loaded /example4-form.html:' + x.responseText) }
