import React from "react";
import * as globals from "./globals";
import _ from "lodash";

class PaginateButton extends React.Component {

  render() {
    return (
      <button
        onClick={this.props.paginate}
        style={{
          marginLeft: this.props.leftSide ? 0 : 10,
          marginRight: this.props.leftSide ? 10 : 0,
          cursor: "pointer",
          border: "none",
          backgroundColor: "white",
          position: "relative",
          top: 5,
        }}>
        <svg width="20px" height="20px" viewBox="0 0 10 10" >
          {this.props.children}
        </svg>
      </button>
    )
  }

}


class TidCarousel extends React.Component {

  constructor(props) {
    super(props);

    this.state = {
      page: 0,
      perPage: 10,
    };
  }

  movePage (n) {
    return () => {
      console.log(this.state.page, this.state.perPage, n)
      if (this.state.page + n <= 0) {
        this.setState({page: 0}) /* min case */
      } else if (this.state.page + n > (this.props.commentsToShow.length / this.state.perPage)) {
        this.setState({page: ((this.props.commentsToShow.length / this.state.perPage) - 1)})
      } else {
        this.setState({page: this.state.page + n})
      }
    }
  }

  render() {

    return (
      <div style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "baseline",
        marginTop: 10,
        }}>
        <div>
          <PaginateButton paginate={this.movePage(-10)} leftSide>
            <g transform="translate(-13.000000, -5.000000)">
              <g transform="translate(13.000000, 5.000000)">
                <polyline fill="rgb(180,180,180)" points="4.50549451 5 9.50549451 10 9.50549451 6.123234e-16"></polyline>
                <polyline fill="rgb(180,180,180)" points="-3.061617e-16 5 5 10 5 6.123234e-16"></polyline>
              </g>
            </g>
          </PaginateButton>
          <PaginateButton paginate={this.movePage(-1)} leftSide>
            <g  transform="translate(-13.000000, -5.000000)">
              <g transform="translate(13.000000, 5.000000)">
                <polyline fill="rgb(180,180,180)" points="-3.061617e-16 5 5 10 5 6.123234e-16"></polyline>
              </g>
            </g>
          </PaginateButton>
        </div>
        <div>
          {
            this.props.commentsToShow && _.map(
              this.props.commentsToShow.slice(
                (this.state.page * this.state.perPage),
                ((this.state.page * this.state.perPage) + this.state.perPage)
              ), (c) => { return (
                <span
                  onClick={this.props.handleCommentClick(c)}
                  style={{
                    cursor: "pointer",
                    marginRight: 5,
                    padding: "6px 12px",
                    fontWeight: (this.props.selectedComment && this.props.selectedComment.tid === c.tid) ? 700 : 500,
                    backgroundColor: (this.props.selectedComment && this.props.selectedComment.tid === c.tid) ? "#03a9f4" : "rgb(235,235,235)",
                    color: (this.props.selectedComment && this.props.selectedComment.tid === c.tid) ? "rgb(255,255,255)" : "rgb(100,100,100)",
                    borderRadius: 4,
                  }}
                  key={c.tid}>
                  {c.tid}
                </span>
              )}
            )
          }
        </div>
        <div>
          <PaginateButton paginate={this.movePage(1)}>
            <g transform="translate(-13.000000, -5.000000)">
              <g transform="translate(18.000000, 10.000000) scale(-1, 1) translate(-18.000000, -10.000000) translate(13.000000, 5.000000)">
                <polyline fill="rgb(180,180,180)" points="-3.061617e-16 5 5 10 5 6.123234e-16"></polyline>
              </g>
            </g>
          </PaginateButton>
          <PaginateButton paginate={this.movePage(10)}>
            <g transform="translate(-13.000000, -5.000000)">
              <g transform="translate(18.000000, 10.000000) scale(-1, 1) translate(-18.000000, -10.000000) translate(13.000000, 5.000000)">
                <polyline fill="rgb(180,180,180)" points="4.50549451 5 9.50549451 10 9.50549451 6.123234e-16"></polyline>
                <polyline fill="rgb(180,180,180)" points="-3.061617e-16 5 5 10 5 6.123234e-16"></polyline>
              </g>
            </g>
          </PaginateButton>
        </div>
      </div>
    )
  }
}

export default TidCarousel;