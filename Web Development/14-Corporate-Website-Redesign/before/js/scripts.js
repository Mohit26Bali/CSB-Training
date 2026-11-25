/* Fernhill College - site scripts (c) 2012 WebSolutionz Ltd */

var current = 0;

function init() {
	// image slider: changes every 3 seconds, cannot be paused
	var slides = document.getElementsByClassName('slide');
	var dots = document.getElementsByClassName('dot');
	if (slides.length > 0) {
		setInterval(function () {
			slides[current].style.display = 'none';
			dots[current].className = 'dot';
			current = (current + 1) % slides.length;
			slides[current].style.display = 'block';
			dots[current].className = 'dot active';
		}, 3000);
	}

	// newsletter popup on every page, 2 seconds after loading
	setTimeout(function () {
		document.getElementById('overlay').style.display = 'block';
	}, 2000);
}

function closePopup() {
	document.getElementById('overlay').style.display = 'none';
}

// rollover images for the navigation buttons
function swap(link, name, over) {
	link.getElementsByTagName('img')[0].src = 'images/nav-' + name + (over ? '-hover' : '') + '.png';
}

function validateForm() {
	var f = document.forms['contactform'];
	if (f.name.value == '' || f.email.value == '' || f.type.value == '' || f.message.value == '' || f.captcha.value == '' || !f.terms.checked) {
		alert('Please fill in all required fields!');
		return false;
	}
	if (f.email.value.indexOf('@') == -1) {
		alert('Invalid email!');
		return false;
	}
	if (f.captcha.value != 'kW7pRx') {
		alert('Incorrect security code. Please try again.');
		return false;
	}
	alert('Thank you! Your enquiry has been sent.');
	return true;
}
