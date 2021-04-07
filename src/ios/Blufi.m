/********* Blufi.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import "BlufiClient.h"
#import "BlufiConfigureParams.h"

@interface Blufi : CDVPlugin <CBCentralManagerDelegate, CBPeripheralDelegate, BlufiDelegate>{
    // Member variables go here.
}
@property(strong, nonatomic)BlufiClient *blufiClient;
@property(strong, nonatomic)NSString *device;
@property(strong, nonatomic)CDVInvokedUrlCommand *mConnectCommand;
@property(strong, nonatomic)CDVInvokedUrlCommand *mConfigCommand;
@property(strong, nonatomic)CDVInvokedUrlCommand *mReadStateCommand;
@property(strong, nonatomic)CDVInvokedUrlCommand *mCustomDataCommand;
@property(strong, nonatomic)CDVInvokedUrlCommand *mDisconnectCommand;
- (void)connect:(CDVInvokedUrlCommand*)command;
- (void)configure:(CDVInvokedUrlCommand*)command;
- (void)disconnect:(CDVInvokedUrlCommand*)command;
- (void)request_device_status:(CDVInvokedUrlCommand*)command;
- (void)custom_data:(CDVInvokedUrlCommand*)command;
@end

@implementation Blufi


- (void)connect:(CDVInvokedUrlCommand*)command
{
    NSLog(@"----Blufi connect----");
    _mConnectCommand = command;
    CDVPluginResult* pluginResult = nil;
    @try {
        _device = [command.arguments objectAtIndex:0];
    } @catch (NSException *exception) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"params_error"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mConnectCommand.callbackId];
        _mConnectCommand = nil;
        return;
    }
    
    if (_device != nil && [_device length] > 0) {
        if (_blufiClient) {
            [_blufiClient close];
            _blufiClient = nil;
        }
        _blufiClient = [[BlufiClient alloc] init];
        _blufiClient.centralManagerDelete = self;
        _blufiClient.peripheralDelegate = self;
        _blufiClient.blufiDelegate = self;
        [_blufiClient connect:_device];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"params_error"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mConnectCommand.callbackId];
        _mConnectCommand = nil;
    }
}

- (void)configure:(CDVInvokedUrlCommand*)command
{
    _mConfigCommand = command;
    if (_blufiClient == nil) {
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"blufi_client_null"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mConfigCommand.callbackId];
        _mConfigCommand = nil;
        return;
    }
    @try {
        //NSString *mode = [command.arguments objectAtIndex:0];
        NSString *ssid = [command.arguments objectAtIndex:1];
        NSString *password = [command.arguments objectAtIndex:2];
        BlufiConfigureParams *param = [[BlufiConfigureParams alloc] init];
        param.opMode= OpModeSta;
        param.staSsid = ssid;
        param.staPassword = password;
        [_blufiClient configure: param];
        NSLog(@"----Blufi configure param:\n%@----", param);
    }
    @catch (NSException *exception) {
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"params_error"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mConfigCommand.callbackId];
        _mConfigCommand = nil;
    }
}

- (void)disconnect:(CDVInvokedUrlCommand*)command
{
    _mDisconnectCommand = command;
    if (_blufiClient != nil) {
        [_blufiClient requestCloseConnection];
    }
}

- (void)request_device_status:(CDVInvokedUrlCommand*)command
{
    _mReadStateCommand = command;
    CDVPluginResult* pluginResult = nil;
    if (_blufiClient == nil) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"blufi_client_null"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mReadStateCommand.callbackId];
        _mReadStateCommand = nil;
        return;
    }
    [_blufiClient requestDeviceStatus];
}

- (void)custom_data:(CDVInvokedUrlCommand*)command
{
    _mCustomDataCommand = command;
    CDVPluginResult* pluginResult = nil;
    if (_blufiClient == nil) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"blufi_client_null"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mCustomDataCommand.callbackId];
        _mCustomDataCommand = nil;
        return;
    }
    NSString *text = nil;
    @try {
        text = [command.arguments objectAtIndex:0];
    } @catch (NSException *exception) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"params_error"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mCustomDataCommand.callbackId];
        _mCustomDataCommand = nil;
        return;
    }
    if (text && text.length > 0 && _blufiClient) {
        NSData *data = [text dataUsingEncoding:NSUTF8StringEncoding];
        [_blufiClient postCustomData:data];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"params_error"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mCustomDataCommand.callbackId];
        _mCustomDataCommand = nil;
    }
}

- (void)centralManagerDidUpdateState:(nonnull CBCentralManager *)central {
    NSLog(@"----Blufi centralManagerDidUpdateState----");
}

- (void)blufi:(BlufiClient *)client gattPrepared:(BlufiStatusCode)status service:(CBService *)service writeChar:(CBCharacteristic *)writeChar notifyChar:(CBCharacteristic *)notifyChar {
    NSLog(@"----Blufi gattPrepared status:%d----", status);
    if (_mConnectCommand == nil){
        return;
    }
    CDVPluginResult* pluginResult = nil;
    if (status == StatusSuccess) {
        NSLog(@"----Blufi gattPrepared BluFi connection has prepared----");
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mConnectCommand.callbackId];
    } else {
        NSString *error;
        if (!service) {
            NSLog(@"----Blufi gattPrepared Discover service failed----");
            error = @"discover_service_failed";
        } else if (!writeChar) {
            NSLog(@"----Blufi gattPrepared Discover write char failed----");
            error = @"discover_write_char_failed";
        } else if (!notifyChar) {
            NSLog(@"----Blufi gattPrepared Discover notify char failed----");
            error = @"discover_notify_char_failed";
        }
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mConnectCommand.callbackId];
    }
    _mConnectCommand = nil;
}

- (void)centralManager:(CBCentralManager *)central didConnectPeripheral:(CBPeripheral *)peripheral {
    NSLog(@"----Blufi didConnectPeripheral Connected device----");
}

- (void)centralManager:(CBCentralManager *)central didFailToConnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error {
    NSLog(@"----Blufi didFailToConnectPeripheral Connet device failed----");
    if (_mConnectCommand == nil){
        return;
    }
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"connect_failed"];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:_mConnectCommand.callbackId];
    _mConnectCommand = nil;
}

- (void)centralManager:(CBCentralManager *)central didDisconnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error {
    NSLog(@"----Blufi didDisconnectPeripheral Disconnected device----");
    if (_mDisconnectCommand == nil){
        return;
    }
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:_mDisconnectCommand.callbackId];
    _mDisconnectCommand = nil;
}

- (void)blufi:(BlufiClient *)client didPostConfigureParams:(BlufiStatusCode)status {
    if (_mConfigCommand == nil){
        return;
    }
    CDVPluginResult* pluginResult = nil;
    if (status == StatusSuccess) {
        NSLog(@"----Blufi didPostConfigureParams Post configure params complete----");
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:_device];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mConfigCommand.callbackId];
    } else {
        NSLog(@"----Blufi didPostConfigureParams Post configure params failed: %d----", status);
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:_device];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_mConfigCommand.callbackId];
    }
    _mConfigCommand = nil;
}

- (void)blufi:(BlufiClient *)client didReceiveDeviceStatusResponse:(BlufiStatusResponse *)response status:(BlufiStatusCode)status {
    NSLog(@"----Blufi didReceiveDeviceStatusResponse Receive device status:\n%d----", status);
    if (_mReadStateCommand == nil) {
        return;
    }
    CDVPluginResult* pluginResult = nil;
    if (status == StatusSuccess) {
        NSLog(@"----Blufi didReceiveDeviceStatusResponse Receive device status:\n%@----", response.getStatusInfo);
        NSString *result = nil;
        if(response.isStaConnectWiFi){
            result = @"{\"connected\":\"true\"}";
        } else {
            result = @"{\"connected\":\"false\"}";
        }
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
    } else {
        NSLog(@"----Blufi didReceiveDeviceStatusResponse Receive device status error: %d----", status);
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"error"];
    }
    [self.commandDelegate sendPluginResult:pluginResult callbackId:_mReadStateCommand.callbackId];
    _mReadStateCommand = nil;
}

- (void)blufi:(BlufiClient *)client didPostCustomData:(nonnull NSData *)data status:(BlufiStatusCode)status {
    if (status == StatusSuccess) {
        NSLog(@"----Blufi didPostCustomData Post custom data complete----");
    } else {
        NSLog(@"----Blufi didPostCustomData Post custom data failed: %d----", status);
    }
}

- (void)blufi:(BlufiClient *)client didReceiveCustomData:(NSData *)data status:(BlufiStatusCode)status {
    NSString *customString = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    NSLog(@"----Blufi didReceiveCustomData Receive device custom data: %@----", customString);
    if (_mCustomDataCommand == nil) {
        return;
    }
    CDVPluginResult* pluginResult = nil;
    if (status == StatusSuccess) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:customString];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"error"];
    }
    [self.commandDelegate sendPluginResult:pluginResult callbackId:_mCustomDataCommand.callbackId];
    _mCustomDataCommand = nil;
}

@end
