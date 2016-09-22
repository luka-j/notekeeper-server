$("#btn-login").click(function() {
    var email = $("#input-email").value;
    var password = $("#input-password").value;

    /*$.ajax({
        type: 'post',
        url: '/users/login',
        data: { "email": email, "pass": password },
        xhrFields: {
            withCredentials: false
        },
        headers: {

        },
        done: function (data) {
            var expDate = new Date();
            expDate.setTime(expDate.getTime()+1209600000);
            document.cookie = "token=" + data.responseText + "; expires=" + expDate.toUTCString();
        },
        error: function () {
            console.error("Login failed, code: " + data.status +", message: " + data.statusText);
        }
    });*/

    $.post("/users/login", JSON.stringify({"email": email, "pass": password}), function(data, status) {
        var expDate = new Date();
        expDate.setTime(expDate.getTime()+1209600000);
        document.cookie = "token=" + data.responseText + "; expires=" + expDate.toUTCString();
    }, "text")
        .fail(function(data, status) {
            if(status==401) {
                var pwdField = $("#input-password");
                pwdField.removeClass("valid");
                pwdField.addClass("invalid");
                console.log("set invalid");
            }
            console.error("Login failed, code: " + data.status +", message: " + data.statusText);
        });
});