<?php

require 'jsonwrapper.php';

$myFile = "/home/gnychis/phone_activity/activity_user_data.txt";
if(!($fh = fopen($myFile, 'a'))) {
  print "FAIL";
  return;
}

$data = file_get_contents('php://input');
$json = json_decode($data);

$clientID = $json->{'clientID'};
$ageRange = $json->{'ageRange'};
$kitchen = $json->{'kitchen'};
$bedroom = $json->{'bedroom'};
$livingRoom = $json->{'livingRoom'};
$bathroom = $json->{'bathroom'};

fprintf($fh, "%s,%s,%s,%s,%s,%s,%s\n", $clientID, $ageRange, $kitchen, $bedroom, $livingRoom, $bathroom);

print "OK";
?>
