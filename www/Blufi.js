var exec = require('cordova/exec');

exports.connect = function (arg0, success, error) {
    console.log('----blufi---connect-----');
    exec(success, error, 'Blufi', 'connect', [arg0]);
};

exports.configure = function (arg0, arg1, arg2, success, error) {
    console.log('----blufi---configure-----');
    exec(success, error, 'Blufi', 'configure', [arg0, arg1, arg2]);
};

exports.disconnect = function (success, error) {
    console.log('----blufi---disconnect-----');
    exec(success, error, 'Blufi', 'disconnect', []);
};

exports.requstDeviceStatus = function (success, error) {
    console.log('----blufi---requstDeviceStatus-----');
    exec(success, error, 'Blufi', 'request_device_status', []);
};

exports.customData = function (arg0, success, error) {
    console.log('----blufi---customData-----');
    exec(success, error, 'Blufi', 'custom_data', [arg0]);
};