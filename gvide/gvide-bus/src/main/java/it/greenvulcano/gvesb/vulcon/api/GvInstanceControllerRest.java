/*******************************************************************************
 * Copyright (c) 2009, 2016 GreenVulcano ESB Open Source Project.
 * All rights reserved.
 *
 * This file is part of GreenVulcano ESB.
 *
 * GreenVulcano ESB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GreenVulcano ESB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GreenVulcano ESB. If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package it.greenvulcano.gvesb.vulcon.api;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.ws.rs.DELETE;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import it.greenvulcano.gvesb.vulcon.bus.BusLink;

@Path("/instance")
public class GvInstanceControllerRest {

	private BusLink busLink;
	
	public void setBusLink(BusLink busLink) {
		this.busLink = busLink;
	}	
	
	@Path("/link")
	@PUT	
	public Response bind(@QueryParam(value="apikey") String key) {
		Response response = null;
		
		if (key==null || key.trim().isEmpty()) {
			response = Response.status(Status.BAD_REQUEST).entity("Missing required param apikey").build();
		} else {
			try {
				response = Response.status(Status.CREATED).entity(busLink.connect(key)).build();
			} catch (IOException e) {
				response = Response.status(Status.SERVICE_UNAVAILABLE).entity(e.getMessage()).build();				
			} catch (GeneralSecurityException e) {
				response = Response.status(Status.CONFLICT).entity(e.getMessage()).build();
			} catch (IllegalArgumentException e) {
				response = Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
			}
		}
		return response;
		
	}
	
	@Path("/link")
	@DELETE
	public Response unbind(@QueryParam(value="apikey") String key) {
		Response response = null; 
		if (key==null || key.trim().isEmpty()) {
			response = Response.status(Status.BAD_REQUEST).entity("Missing required param apikey").build();
		} else {
			 try {
				response = Response.status(Status.CREATED).entity(busLink.disconnect(key)).build();
			} catch (IOException e) {
				response = Response.status(Status.SERVICE_UNAVAILABLE).entity(e.getMessage()).build();				
			} catch (GeneralSecurityException e) {
				response = Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
			} catch (IllegalArgumentException e) {
				response = Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
			}
		}
		return response;
	}

}
