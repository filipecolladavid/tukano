"use strict";

module.exports = {
	uploadRandomizedUser,
	processRegisterReply,
	createRandomShort,
	processShortReply,
	generateRandomBlob,
	processBlobReply,
};

var registeredUsers = [];

var statsPrefix = [
	["/rest/media/", "GET"],
	["/rest/media", "POST"],
];

global.myProcessEndpoint = function (str, method) {
	var i = 0;
	for (i = 0; i < statsPrefix.length; i++) {
		if (str.startsWith(statsPrefix[i][0]) && method == statsPrefix[i][1])
			return method + ":" + statsPrefix[i][0];
	}
	return method + ":" + str;
};

function randomUsername(char_limit) {
	const letters = "abcdefghijklmnopqrstuvwxyz";
	let username = "";
	let num_chars = Math.floor(Math.random() * char_limit);
	for (let i = 0; i < num_chars; i++) {
		username += letters[Math.floor(Math.random() * letters.length)];
	}
	return username;
}

function randomPassword(pass_len) {
	const skip_value = 33;
	const lim_values = 94;

	let password = "";
	for (let i = 0; i < pass_len; i++) {
		let chosen_char = Math.floor(Math.random() * lim_values) + skip_value;
		if (chosen_char == "'" || chosen_char == '"') i -= 1;
		else password += chosen_char;
	}
	return password;
}

function processRegisterReply(requestParams, response, context, ee, next) {
	if (typeof response.body !== "undefined" && response.body.length > 0) {
		registeredUsers.push(response.body);
	}
	return next();
}

function uploadRandomizedUser(requestParams, context, ee, next) {
	let username = randomUsername(10);
	let pword = randomPassword(15);
	let email = username + "@campus.fct.unl.pt";

	const user = {
		userId: username,
		pwd: pword,
		email: email,
		displayName: username,
	};
	requestParams.body = JSON.stringify(user);

	context.vars.lastRegisteredUserId = username;
	return next();
}

var createdShorts = [];

function createRandomShort(requestParams, context, ee, next) {
	const shortId = `short_${Math.floor(Math.random() * 10000)}`;
	const ownerId = context.vars.lastRegisteredUserId;

	const content = {
		shortId: shortId,
		ownerId: ownerId,
		blobUrl: `https://randomdoesntmatter/${shortId}`,
		timestamp: Date.now(),
		totalLikes: 0,
		views: 0,
	};

	requestParams.body = JSON.stringify(content);
	return next();
}

function processShortReply(requestParams, response, context, ee, next) {
	if (typeof response.body !== "undefined" && response.body.length > 0) {
		createdShorts.push(response.body);
		if (response.body.shortId) {
			context.vars.lastShortId = response.body.shortId;
		}
	}
	return next();
}

function generateRandomBlob(requestParams, context, ee, next) {
	const blobSize = Math.floor(Math.random() * 1024) + 512;
	const blob = Buffer.alloc(blobSize);
	for (let i = 0; i < blobSize; i++) {
		blob[i] = Math.floor(Math.random() * 256);
	}
	requestParams.body = blob;
	return next();
}

function processBlobReply(requestParams, response, context, ee, next) {
	if (typeof response.body !== "undefined" && response.body.length > 0) {
		const blobInfo = JSON.parse(response.body);

		context.vars.lastUploadedBlobUrl = blobInfo.blobUrl;

		if (!context.vars.uploadedBlobs) {
			context.vars.uploadedBlobs = [];
		}
		context.vars.uploadedBlobs.push(blobInfo);
	}
	return next();
}
