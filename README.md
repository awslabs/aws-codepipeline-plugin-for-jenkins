AWS CodePipeline Jenkins Plugin
=============================
The AWS CodePipeline Plugin for Jenkins provides a pre-build SCM and a post-build step
for your Jenkins project. It will poll and download changes. When a build succeeds it
will zip the build artifacts and upload them to AWS CodePipeline.

Setting up
----------
After installing the plugin, some simple configuration is needed for your project:

1. Open up your project configuration.
2. In the `Source Code Management` section, select "AWS CodePipeline".
3. Fill out all the fields there as required.
4. Configure your build as you normally would.
5. In Post-Build Action, select AWS CodePipeline Publisher.
    * If you only want to upload the status, don't add any output locations.
    * If you want to upload the whole workspace, add a location but leave it blank.
    * Otherwise, specify the folder location where you want to upload to.

Notes
-----
This Plugin uses Java 8.

License
-------
This plugin is open sourced and licensed under Apache 2.0. See the LICENSE file for more information.
