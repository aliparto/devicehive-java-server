package com.devicehive.controller;

import com.devicehive.auth.AllowedKeyAction;
import com.devicehive.auth.HivePrincipal;
import com.devicehive.auth.HiveRoles;
import com.devicehive.auth.HiveAuthentication;
import com.devicehive.configuration.Messages;
import com.devicehive.controller.converters.SortOrderQueryParamParser;
import com.devicehive.controller.util.ResponseFactory;
import com.devicehive.json.strategies.JsonPolicyDef;
import com.devicehive.model.ErrorResponse;
import com.devicehive.model.Network;
import com.devicehive.model.updates.NetworkUpdate;
import com.devicehive.service.NetworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;

import static com.devicehive.auth.AllowedKeyAction.Action.GET_NETWORK;
import static com.devicehive.auth.AllowedKeyAction.Action.MANAGE_NETWORK;
import static com.devicehive.configuration.Constants.*;
import static javax.ws.rs.core.Response.Status.*;

@Singleton
@Path("/network")
public class NetworkController {
    private static final Logger logger = LoggerFactory.getLogger(NetworkController.class);

    @Autowired
    private NetworkService networkService;

    /**
     * Produces following output:
     * <pre>
     * [
     *  {
     *    "description":"Network Description",
     *    "id":1,
     *    "key":"Network Key",
     *    "name":"Network Name"
     *   },
     *   {
     *    "description":"Network Description",
     *    "id":2,
     *    "key":"Network Key",
     *    "name":"Network Name"
     *   }
     * ]
     * </pre>
     *
     * @param name        exact network's name, ignored, when  namePattern is not null
     * @param namePattern name pattern
     * @param sortField   Sort Field, can be either "id", "key", "name" or "description"
     * @param sortOrderSt ASC - ascending, otherwise descending
     * @param take        limit, default 1000
     * @param skip        offset, default 0
     */
    @GET
    @RolesAllowed({HiveRoles.CLIENT, HiveRoles.ADMIN, HiveRoles.KEY})
    @AllowedKeyAction(action = GET_NETWORK)
    public Response getNetworkList(@QueryParam(NAME) String name,
                                   @QueryParam(NAME_PATTERN) String namePattern,
                                   @QueryParam(SORT_FIELD) String sortField,
                                   @QueryParam(SORT_ORDER) String sortOrderSt,
                                   @QueryParam(TAKE) Integer take,
                                   @QueryParam(SKIP) Integer skip) {

        logger.debug("Network list requested");

        boolean sortOrder = SortOrderQueryParamParser.parse(sortOrderSt);

        if (sortField != null && !ID.equalsIgnoreCase(sortField) && !NAME.equalsIgnoreCase(sortField)) {
            logger.error("Unable to proceed network list request. Invalid sortField");
            return ResponseFactory.response(Response.Status.BAD_REQUEST,
                    new ErrorResponse(Messages.INVALID_REQUEST_PARAMETERS));
        } else if (sortField != null) {
            sortField = sortField.toLowerCase();
        }
        HivePrincipal principal = (HivePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<Network> result = networkService
            .list(name, namePattern, sortField, sortOrder, take, skip, principal);

        logger.debug("Network list request proceed successfully.");
        return ResponseFactory.response(Response.Status.OK, result, JsonPolicyDef.Policy.NETWORKS_LISTED);
    }

    /**
     * Generates  JSON similar to this:
     * <pre>
     *     {
     *      "description":"Network Description",
     *      "id":1,
     *      "key":"Network Key",
     *      "name":"Network Name"
     *     }
     * </pre>
     *
     * @param id network id, can't be null
     */
    @GET
    @Path("/{id}")
    @RolesAllowed({HiveRoles.CLIENT, HiveRoles.ADMIN, HiveRoles.KEY})
    @AllowedKeyAction(action = GET_NETWORK)
    public Response getNetwork(@PathParam(ID) long id) {
        logger.debug("Network get requested.");
        Network existing = networkService.getWithDevicesAndDeviceClasses(id, (HiveAuthentication) SecurityContextHolder.getContext().getAuthentication());
        if (existing == null) {
            logger.error("Network with id =  {} does not exists", id);
            return ResponseFactory.response(Response.Status.NOT_FOUND, new ErrorResponse(NOT_FOUND.getStatusCode(),
                    String.format(Messages.NETWORK_NOT_FOUND, id)));
        }
        return ResponseFactory.response(OK, existing, JsonPolicyDef.Policy.NETWORK_PUBLISHED);
    }

    /**
     * Inserts new Network into database. Consumes next input:
     * <pre>
     *     {
     *       "key":"Network Key",
     *       "name":"Network Name",
     *       "description":"Network Description"
     *     }
     * </pre>
     * Where "key" is not required "description" is not required "name" is required <p/> In case of success will produce
     * following output:
     * <pre>
     *     {
     *      "description":"Network Description",
     *      "id":1,
     *      "key":"Network Key",
     *      "name":"Network Name"
     *     }
     * </pre>
     * Where "description" and "key" will be provided, if they are specified in request. Fields "id" and "name" will be
     * provided anyway.
     */
    @POST
    @RolesAllowed({HiveRoles.ADMIN, HiveRoles.KEY})
    @AllowedKeyAction(action = MANAGE_NETWORK)
    public Response insert(Network network) {
        logger.debug("Network insert requested");
        Network result = networkService.create(network);
        logger.debug("New network has been created");
        return ResponseFactory.response(CREATED, result, JsonPolicyDef.Policy.NETWORK_SUBMITTED);
    }

    /**
     * This method updates network with given Id. Consumes following input:
     * <pre>
     *     {
     *       "key":"Network Key",
     *       "name":"Network Name",
     *       "description":"Network Description"
     *     }
     * </pre>
     * Where "key" is not required "description" is not required "name" is not required Fields, that are not specified
     * will stay unchanged Method will produce following output:
     * <pre>
     *     {
     *      "description":"Network Description",
     *      "id":1,
     *      "key":"Network Key",
     *      "name":"Network Name"
     *     }
     * </pre>
     */
    @PUT
    @Path("/{id}")
    @RolesAllowed({HiveRoles.ADMIN, HiveRoles.KEY})
    @AllowedKeyAction(action = MANAGE_NETWORK)
    public Response update(NetworkUpdate networkToUpdate, @PathParam(ID) long id) {
        logger.debug("Network update requested. Id : {}", id);
        networkService.update(id, networkToUpdate);
        logger.debug("Network has been updated successfully. Id : {}", id);
        return ResponseFactory.response(NO_CONTENT);
    }

    /**
     * Deletes network by specified id. If success, outputs empty response
     *
     * @param id network's id
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed({HiveRoles.ADMIN, HiveRoles.KEY})
    @AllowedKeyAction(action = MANAGE_NETWORK)
    public Response delete(@PathParam(ID) long id) {
        logger.debug("Network delete requested");
        networkService.delete(id);
        logger.debug("Network with id = {} does not exists any more.", id);
        return ResponseFactory.response(NO_CONTENT);
    }
}