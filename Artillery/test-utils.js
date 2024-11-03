'use strict';

/***
 * Exported functions to be used in the testing scripts.
 */
module.exports = {
    uploadRandomizedUser,
    processRegisterReply,
    createRandomShort,
    processShortReply,
    generateRandomBlob,
    processBlobReply
}



var registeredUsers = []

// All endpoints starting with the following prefixes will be aggregated in the same for the statistics
var statsPrefix = [ ["/rest/media/","GET"],
    ["/rest/media","POST"]
]

// Function used to compress statistics
global.myProcessEndpoint = function( str, method) {
    var i = 0;
    for( i = 0; i < statsPrefix.length; i++) {
        if( str.startsWith( statsPrefix[i][0]) && method == statsPrefix[i][1])
            return method + ":" + statsPrefix[i][0];
    }
    return method + ":" + str;
}

// Returns a random username constructed from lowercase letters.
function randomUsername(char_limit){
    const letters = 'abcdefghijklmnopqrstuvwxyz';
    let username = '';
    let num_chars = Math.floor(Math.random() * char_limit);
    for (let i = 0; i < num_chars; i++) {
        username += letters[Math.floor(Math.random() * letters.length)];
    }
    return username;
}


// Returns a random password, drawn from printable ASCII characters
function randomPassword(pass_len){
    const skip_value = 33;
    const lim_values = 94;

    let password = '';
    let num_chars = Math.floor(Math.random() * pass_len);
    for (let i = 0; i < pass_len; i++) {
        let chosen_char =  Math.floor(Math.random() * lim_values) + skip_value;
        if (chosen_char == "'" || chosen_char == '"')
            i -= 1;
        else
            password += chosen_char
    }
    return password;
}

/**
 * Process reply of the user registration.
 */
function processRegisterReply(requestParams, response, context, ee, next) {
    if( typeof response.body !== 'undefined' && response.body.length > 0) {
        registeredUsers.push(response.body);
    }
    return next();
}

/**
 * Register a random user.
 */

function uploadRandomizedUser(requestParams, context, ee, next) {
    let username = randomUsername(10);
    let pword = randomPassword(15);
    let email = username + "@campus.fct.unl.pt";
    let displayName = username;

    const user = {
        userId: username,
        pwd: pword,
        email: email,
        displayName: username
    };
    requestParams.body = JSON.stringify(user);
    return next();
}

// Array to store created shorts
var createdShorts = [];

/**
 * Creates a random short with some content
 */
function createRandomShort(requestParams, context, ee, next) {
    // Generate random content for the short
    const shortId = `short_${Math.floor(Math.random() * 10000)}`; // Unique identifier for the short
    const ownerId = context.vars.lastRegisteredUserId; // Assume we have the last registered user ID in context

    const content = {
        shortId: shortId,
        ownerId: ownerId,
        blobUrl: `https://randomdoesntmatter/${shortId}`, // Example blob URL
        timestamp: Date.now(), // Current timestamp
        totalLikes: 0, // Initialize likes
        views: 0 // Initialize views
    };

    requestParams.body = JSON.stringify(content);
    return next();
}

/**
 * Process reply after creating a short
 */
function processShortReply(requestParams, response, context, ee, next) {
    if (typeof response.body !== 'undefined' && response.body.length > 0) {
        createdShorts.push(response.body);
        // Store the shortId in context if needed
        if (response.body.shortId) {
            context.vars.lastShortId = response.body.shortId;
        }
    }
    return next();
}


/**
 * Generates a random blob of data for testing blob uploads
 */
function generateRandomBlob(requestParams, context, ee, next) {
    const blobSize = Math.floor(Math.random() * 1024) + 512; // Random size between 512 and 1536 bytes
    const blob = Buffer.alloc(blobSize);
    for (let i = 0; i < blobSize; i++) {
        blob[i] = Math.floor(Math.random() * 256);
    }
    requestParams.body = blob;
    return next();
}

/**
 * Process reply after uploading a blob
 */
function processBlobReply(requestParams, response, context, ee, next) {
    if (typeof response.body !== 'undefined' && response.body.length > 0) {
        const blobInfo = JSON.parse(response.body);

        // Example: Log the blob ID or URL
        console.log("Blob uploaded successfully:", blobInfo);

        // Optionally store blob information for further use
        context.vars.lastUploadedBlobUrl = blobInfo.blobUrl; // Adjust based on actual response structure

        // You can also keep track of all uploaded blobs if needed
        if (!context.vars.uploadedBlobs) {
            context.vars.uploadedBlobs = [];
        }
        context.vars.uploadedBlobs.push(blobInfo);
    }
    return next();
}

