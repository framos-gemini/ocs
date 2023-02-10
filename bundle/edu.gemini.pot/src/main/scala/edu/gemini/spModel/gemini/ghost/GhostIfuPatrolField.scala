// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.spModel.gemini.ghost

import edu.gemini.spModel.core.{Angle, Coordinates, Offset, OffsetP, OffsetQ}
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.telescope.IssPort

import java.awt.geom.{AffineTransform, Area, Rectangle2D}
import scala.collection.JavaConverters._


sealed trait GhostIfuPatrolField {

  def area: Area

  def inRange(offset: Offset): Boolean = {
    val x = -offset.p.toAngle.toSignedArcsecs
    val y = -offset.q.toAngle.toSignedArcsecs
    area.contains(x, y)
  }

  def ifuInRange(base: Coordinates, ifu: Coordinates): Boolean =
    inRange(Coordinates.difference(base, ifu).offset)

}

object GhostIfuPatrolField {

  val ScaleMmToArcsec: Double = 1.0 / 0.61  // arcsec / mm
  val TransformMmToArcsec: AffineTransform =
    AffineTransform.getScaleInstance(ScaleMmToArcsec, ScaleMmToArcsec)

  // From REL-4199 there are offsets to be applied for p2 checks
  val SRIFU1SeparationOffset: Offset =
    Offset(OffsetP(Angle.fromArcsecs(-ScaleMmToArcsec)), OffsetQ(Angle.fromArcsecs(ScaleMmToArcsec)))

  val SRIFU2SeparationOffset: Offset =
    Offset(OffsetP(Angle.zero), OffsetQ(Angle.fromArcsecs(ScaleMmToArcsec)))

  val HRIFUSeparationOffset: Offset =
    Offset(OffsetP(Angle.zero), OffsetQ(Angle.fromArcsecs(-ScaleMmToArcsec)))

  val HRSkySeparationOffset: Offset =
    Offset(OffsetP(Angle.zero), OffsetQ(Angle.fromArcsecs(-ScaleMmToArcsec)))
  // End offsets for REL-4199

  val HrSkyFiberOffset: Angle =
    Angle.fromArcsecs(2.0 * ScaleMmToArcsec)

    // The patrol fields for IFU1 and IFU2 relative to the base position.
  private val ifu1PatrolFieldRectangle = new Rectangle2D.Double(-222, -222, 222 + 3.28, 444)
  private val ifu2PatrolFieldRectangle = new Rectangle2D.Double(-3.28, -222, 222 + 3.28, 444)

  private def patrolFieldArea(
    ctx:  ObsContext,
    rect: Rectangle2D.Double
  ): Area = {

    val rot: Angle =
      if (ctx.getIssPort == IssPort.SIDE_LOOKING) Angle.angle90 else Angle.zero

    val θ: Double =
      ctx.getPositionAngle.toRadians + rot.toRadians

    val rects = ctx.getSciencePositions.asScala.toSet.map { pos: edu.gemini.skycalc.Offset =>
      val fov          = new Area(GhostScienceAreaGeometry.Fov)
      val offsetPatrol = new Area(rect)

      val p = pos.p.toArcsecs.getMagnitude
      val q = pos.q.toArcsecs.getMagnitude
      val xform = new AffineTransform
      if (θ != 0.0) xform.rotate(-θ)
      xform.translate(-p, -q)

      fov.transform(xform)
      offsetPatrol.transform(xform)
      offsetPatrol.intersect(fov)
      offsetPatrol
    }

    rects.reduceOption { (a, b) =>
      val c = new Area(a)
      c.intersect(b)
      c
    }.getOrElse(new Area)
  }

  def ifu1(ctx: ObsContext): GhostIfuPatrolField = new GhostIfuPatrolField {
    override val area: Area =
      patrolFieldArea(ctx, ifu1PatrolFieldRectangle)
  }

  def ifu2(ctx: ObsContext): GhostIfuPatrolField = new GhostIfuPatrolField {
    override val area: Area =
      patrolFieldArea(ctx, ifu2PatrolFieldRectangle)
  }

}


