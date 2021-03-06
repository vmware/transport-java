/*
 * Copyright 2019-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package samples.calendar;

import com.vmware.transport.bridge.Request;
import com.vmware.transport.bridge.Response;
import com.vmware.transport.bus.model.Message;
import com.vmware.transport.core.AbstractService;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

@Component
public class CalendarService extends AbstractService<Request<String>, Response<String>> {

    // define the channel the service operates on,.
    public static final String Channel = "calendar-service";

    CalendarService() {
        super(CalendarService.Channel);
    }
    protected void handleServiceRequest(Request request, Message busMessage) {
        // which command shall we run?
        switch(request.getRequest()) {
            case SampleCommand.Date:
                handleDate(request);
                break;

            case SampleCommand.Time:
                handleTime(request);
                break;

            default:
                this.handleUnknownRequest(request);
        }
    }

    private String formatCalendar(String format) {
        Calendar calendar = GregorianCalendar.getInstance();
        SimpleDateFormat fmt = new SimpleDateFormat(format);
        return fmt.format(calendar.getTime());
    }

    private void handleTime(Request request) {
        Response<String> response = new Response<>(request.getId(), formatCalendar("hh:mm:ss.SSS a (Z)"));
        this.sendResponse(response, request.getId());
    }

    private void handleDate(Request request) {
        Response<String> response = new Response<>(request.getId(), formatCalendar("EEE, d MMM yyyy"));
        this.sendResponse(response, request.getId());
    }
}

abstract class SampleCommand {
    static final String Time = "time";
    static final String Date = "date";
}
