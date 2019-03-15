import React, { useState } from 'react';
import { useBifrost } from '../../../../react-bifrost';
import { PongRequestType, PongServiceChannel, PongServiceRequest, PongServiceResponse } from '../ts/ping-component/pong.service.model';
import { GeneralUtil } from '@vmw/bifrost/util/util';

export default function PingComponent() {

    const bifrost = useBifrost();
    const [ pingResponse, setPingResponse ] = useState<string>('nothing yet, hit a button');

    function sendPingBasic() {
        sendPingRequest(bifrost.fabric.generateFabricRequest(PongRequestType.Basic, 'basic ping'));
    }

    function sendPingFull() {
        sendPingRequest(bifrost.fabric.generateFabricRequest(PongRequestType.Full, 'full ping'));
    }

    function sendPingRequest(request: PongServiceRequest) {
        bifrost.bus.requestOnceWithId(GeneralUtil.genUUIDShort(), PongServiceChannel, request)
            .handle(
                (response: PongServiceResponse) => {
                    setPingResponse(response.payload);
                }
            );
    }

    return (
        <div>
            <button onClick={() => sendPingBasic()} className='btn btn-primary'>Ping (Basic)</button>
            <button onClick={() => sendPingFull()} className='btn btn-primary'>Ping (Full)</button>
            <br/>
            Response: {pingResponse}
        </div>
    );
}