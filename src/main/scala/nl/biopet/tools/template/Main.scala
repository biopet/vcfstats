package nl.biopet.tools.template

import nl.biopet.utils.tool.ToolCommand

object Main extends ToolCommand {
  def main(args: Array[String]): Unit = {
    val parser = new ArgsParser(
      this.getClass.getPackage.getName.split(".").last)
    val cmdArgs =
      parser.parse(args, Args()).getOrElse(throw new IllegalArgumentException)

    logger.info("Start")

    //TODO: Execute code

    logger.info("Done")
  }
}
