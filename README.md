# AWS CodePipeline Jenkins Plugin

The AWS CodePipeline plugin for Jenkins provides a pre-build SCM and a
post-build (publisher) step for your Jenkins project.  It will poll for AWS
CodePipeline jobs, and download input artifacts.  When a build succeeds, it
will compress the build artifacts and upload them to AWS CodePipeline.

## Set up

1. Install the `AWS CodePipeline` plugin.
2. Open your project configuration, or create a new project.
3. In the `Source Code Management` section, select **AWS CodePipeline**.
    * Fill out the required fields.
4. In the `Build Trigger` section, select **Poll SCM**.
    * Define a schedule using cron syntax.
5. Configure your build step as you normally would.
6. In the `Post-build Actions` section, add **AWS CodePipeline Publisher**.
    * Configure any output artifacts (see below).

### AWS CodePipeline Publisher

The publisher can upload zero to five output artifacts.

If you don't need to upload output artifacts, don't add any output locations
(but do add **AWS CodePipeline Publisher** as a Post-build action).

To upload output artifacts, add an output location per artifact:

* If the location is blank: the whole workspace will be compressed, and
  uploaded.
* If the location is a directory: the directory will be compressed, and
  uploaded.
* If the location is a normal file: the file will be uploaded as-is (no
  compression).

#### Archive format

For blank (workspace) or directory output locations, the plugin will use the
same archive format used by the input artifacts.  If the input archive type
could not be determined, it will default to ZIP.

Supported archive formats:

* zip
* tar
* tar.gz

## License

This plugin is open sourced and licensed under Apache 2.0. See the LICENSE file
for more information.
