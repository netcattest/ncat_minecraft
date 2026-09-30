function wdExecRequest(name, func) {
	window.cefQuery({	request: "NcatMinecraft_" + name,
						persistent: true,
						onSuccess: function(response) {
							try {
								func(JSON.parse(response));
							} catch(e) {
								document.write(response + "<br/>" + e);
							}
						},
						onFailure: function(errCode, errMsg) {
							document.write(errMsg);
						}});
}

function wdGetSize(callback) {
	wdExecRequest("GetSize", function(size) {
		callback(size.x, size.y);
	});
}

function wdGetUpgrades(callback) {
	wdExecRequest("GetUpgrades", function(resp) {
		callback(resp.upgrades);
	});
}

function wdIsOwner(callback) {
	wdExecRequest("IsOwner", function(resp) {
		callback(resp.isOwner);
	});
}

function wdGetRotation(callback) {

	wdExecRequest("GetRotation", function(resp) {
		callback(resp.rotation);
	});
}

function wdGetSide(callback) {

	wdExecRequest("GetSide", function(resp) {
		callback(resp.side);
	});
}

function wdGetRedstoneAt(x, y, callback) {
	wdExecRequest("GetRedstoneAt(" + x + "," + y + ")", function(resp) {
		callback(resp.level);
	});
}

function wdGetRedstoneArray(callback) {
	wdExecRequest("GetRedstoneArray", function(resp) {
		callback(resp.levels);
	});
}

function wdClearRedstone() {
	wdExecRequest("ClearRedstone", function(resp) {  });
}

function wdSetRedstoneAt(x, y, state) {
	var istate = state ? 1 : 0;
	wdExecRequest("SetRedstoneAt(" + x + "," + y + "," + istate + ")", function(resp) {  });
}

function wdIsEmitting(x, y, callback) {
    wdExecRequest("IsEmitting(" + x + "," + y + ")", function(resp) {
        callback(resp.emitting);
    });
}

function wdGetEmissionArray(callback) {
    wdExecRequest("GetEmissionArray", function(resp) {
        var emission = [];
        for(i = 0; i < resp.emission.length; i++)
            emission.push(resp.emission[i] != 0);

        callback(emission);
    });
}

function wdGetLocation(callback) {
	wdExecRequest("GetLocation", function(resp) {
		callback(resp.x, resp.y, resp.z, resp.side);
	});
}
