<?php

require 'jsonwrapper.php';

$data = file_get_contents('php://input');
$json = json_decode($data);

$clientID = $json->{'clientID'};

$myFile = "/home/gnychis/phone_activity/" . $clientID . ".dat";
if(!($fh = fopen($myFile, 'a'))) {
  print "FAIL";
  return;
}

$cdata = $json->{'data'};
fprintf($fh, "%s", $cdata);

print "OK";
?>
